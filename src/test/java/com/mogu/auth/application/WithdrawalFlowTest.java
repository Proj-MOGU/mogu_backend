package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mogu.auth.domain.*;
import com.mogu.auth.support.MutableAuthClock;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.common.event.*;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class WithdrawalFlowTest {
    private final MutableAuthClock clock = new MutableAuthClock();
    private final MemberId id = new MemberId(UUID.randomUUID());
    private final DiHash di = new DiHash("a".repeat(64));
    private final SocialIdentity social = new SocialIdentity(AuthProvider.KAKAO, "first");
    private final AuthAccount withdrawn =
            AuthAccount.register(id, di, social, clock.instant()).withdraw(clock.instant());
    private final AuthAccountRepository accounts = mock(AuthAccountRepository.class);
    private final SanctionedDiRepository sanctions = mock(SanctionedDiRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    @Test
    @DisplayName("탈퇴 계정의 아직 활성인 갱신 토큰은 패밀리 폐기 후 거절한다")
    void rejectsActiveRefresh() {
        // given
        String raw = "a".repeat(43);
        String hash = RefreshTokenHasher.hash(raw);
        var token = RefreshToken.issue(
                UUID.randomUUID(), UUID.randomUUID(), id, hash, clock.instant(), Duration.ofDays(30));
        when(tokens.markUsedIfActive(hash, clock.instant())).thenReturn(true);
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(accounts.findByMemberId(id)).thenReturn(Optional.of(withdrawn));
        var issuer = mock(RefreshTokenIssuer.class);
        var access = mock(AccessTokenIssuer.class);
        // when / then
        assertThat(new RefreshService(tokens, accounts, issuer, access, clock).refresh(raw))
                .isInstanceOf(RefreshOutcome.Invalid.class);
        verify(tokens).revokeFamily(token.familyId(), clock.instant());
        verifyNoInteractions(issuer, access);
        assertThatThrownBy(() -> new AccountInfoService(accounts).getAccount(id))
                .isInstanceOfSatisfying(
                        BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("탈퇴 뒤 제재와 해제는 DI 목록만 변경하고 계정을 저장하지 않는다")
    void sanctionAndLiftOnlyChangeDiList() {
        // given
        when(accounts.findByMemberIdForUpdate(id)).thenReturn(Optional.of(withdrawn));
        var handler = new SanctionEventHandler(accounts, sanctions, tokens, clock);
        // when
        handler.onSanctioned(new MemberSanctioned(id, null));
        handler.onLifted(new MemberSanctionLifted(id));
        // then
        verify(sanctions).upsert(di, id, null, clock.instant());
        verify(sanctions).delete(di);
        verify(accounts, never()).save(any());
        verifyNoInteractions(tokens);
        reset(sanctions);
        when(accounts.findByMemberIdForUpdate(id)).thenReturn(Optional.of(withdrawn.clearDiHash()));
        handler.onSanctioned(new MemberSanctioned(id, null));
        handler.onLifted(new MemberSanctionLifted(id));
        verifyNoInteractions(sanctions);
    }

    @Test
    @DisplayName("29일은 재가입 제한이며 정각에는 옛 DI 저장 후 새 회원 이벤트를 발행한다")
    void rejoinBoundarySavesOldAccountFirst() {
        // given
        var used = mock(UsedIdentityVerificationRepository.class);
        when(used.markUsed(anyString(), any())).thenReturn(true);
        when(accounts.findByDiHash(di)).thenReturn(Optional.of(withdrawn));
        when(accounts.findByMemberIdForUpdate(id)).thenReturn(Optional.of(withdrawn));
        var generator = mock(MemberIdGenerator.class);
        var newId = new MemberId(UUID.randomUUID());
        when(generator.generate()).thenReturn(newId);
        var service = new RegistrationCompletionService(
                mock(RegistrationLock.class),
                used,
                accounts,
                sanctions,
                generator,
                publisher,
                mock(AccessTokenIssuer.class),
                mock(RefreshTokenIssuer.class),
                clock,
                new WithdrawalPolicy(Duration.ofDays(30)));
        clock.advance(Duration.ofDays(29));
        // when / then
        assertThat(service.complete("first", di, social, null, null, null, false))
                .isInstanceOf(RegistrationOutcome.RejoinRestricted.class);
        verify(accounts, never()).save(any());
        verifyNoInteractions(publisher);
        clock.advance(Duration.ofDays(1));
        assertThat(service.complete("second", di, social, null, null, null, false))
                .isInstanceOf(RegistrationOutcome.Completed.class);
        var order = inOrder(accounts, publisher);
        order.verify(accounts).save(argThat(a -> a.memberId().equals(id) && a.diHash() == null));
        order.verify(accounts)
                .save(argThat(a -> a.memberId().equals(newId) && a.diHash().equals(di)));
        order.verify(publisher).publishEvent(new MemberRegistered(newId, clock.instant()));
    }

    @Test
    @DisplayName("탈퇴 기간이 끝나도 제재 DI 확인을 먼저 하며 배치는 설정한 기한을 사용한다")
    void sanctionsTakePriorityAndCleanupUsesConfiguration() {
        // given
        var used = mock(UsedIdentityVerificationRepository.class);
        when(used.markUsed(anyString(), any())).thenReturn(true);
        when(sanctions.isSanctioned(di, clock.instant())).thenReturn(true);
        var service = new RegistrationCompletionService(
                mock(RegistrationLock.class),
                used,
                accounts,
                sanctions,
                mock(MemberIdGenerator.class),
                publisher,
                mock(AccessTokenIssuer.class),
                mock(RefreshTokenIssuer.class),
                clock,
                new WithdrawalPolicy(Duration.ofDays(30)));
        // when / then
        assertThat(service.complete("verification", di, social, null, null, null, false))
                .isInstanceOf(RegistrationOutcome.Sanctioned.class);
        verify(accounts).findByDiHash(di);
        verify(accounts, never()).findByMemberIdForUpdate(any());
        verify(accounts, never()).save(any());
        verifyNoInteractions(publisher);
        new WithdrawnDiHashCleanup(accounts, new WithdrawalPolicy(Duration.ofDays(7)), clock).cleanup();
        verify(accounts).clearExpiredWithdrawnDiHashes(clock.instant().minus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("재가입은 기존 계정을 잠금 재조회한 뒤 제재 DI를 확인하여 제재 응답을 반환한다")
    void checksSanctionsAfterLockingExistingAccount() {
        // given
        var used = mock(UsedIdentityVerificationRepository.class);
        when(used.markUsed(anyString(), any())).thenReturn(true);
        when(accounts.findByDiHash(di)).thenReturn(Optional.of(withdrawn));
        when(accounts.findByMemberIdForUpdate(id)).thenAnswer(call -> {
            // 잠금 대기 중 제재 이벤트가 커밋된 상황을 모사한다.
            when(sanctions.isSanctioned(di, clock.instant())).thenReturn(true);
            return Optional.of(withdrawn);
        });
        var service = new RegistrationCompletionService(
                mock(RegistrationLock.class),
                used,
                accounts,
                sanctions,
                mock(MemberIdGenerator.class),
                publisher,
                mock(AccessTokenIssuer.class),
                mock(RefreshTokenIssuer.class),
                clock,
                new WithdrawalPolicy(Duration.ofDays(30)));
        clock.advance(Duration.ofDays(31));
        // when
        var outcome = service.complete("verification", di, social, null, null, null, false);
        // then
        assertThat(outcome).isInstanceOf(RegistrationOutcome.Sanctioned.class);
        var order = inOrder(accounts, sanctions);
        order.verify(accounts).findByDiHash(di);
        order.verify(accounts).findByMemberIdForUpdate(id);
        order.verify(sanctions).isSanctioned(di, clock.instant());
        verify(accounts, never()).save(any());
        verifyNoInteractions(publisher);
    }
}
