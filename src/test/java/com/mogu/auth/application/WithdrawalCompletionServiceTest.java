package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mogu.auth.domain.*;
import com.mogu.auth.support.MutableAuthClock;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.common.event.MemberWithdrawn;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class WithdrawalCompletionServiceTest {
    private final AuthAccountRepository accounts = mock(AuthAccountRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final SocialTokenVerificationService verification = mock(SocialTokenVerificationService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final MutableAuthClock clock = new MutableAuthClock();
    private final WithdrawalCompletionService service =
            new WithdrawalCompletionService(accounts, tokens, verification, publisher, clock);
    private final MemberId id = new MemberId(UUID.randomUUID());
    private final VerifiedSocialIdentity reauth = new VerifiedSocialIdentity(AuthProvider.KAKAO, "mine", "nonce");
    private final AuthAccount account = AuthAccount.register(
            id, new DiHash("a".repeat(64)), new SocialIdentity(reauth.provider(), reauth.subject()), clock.instant());

    @Test
    @DisplayName("재인증 후 마이크로초 시각으로 저장과 토큰 폐기 및 이벤트를 순서대로 수행한다")
    void withdrawsInOrder() {
        // given
        clock.advance(Duration.ofNanos(999));
        when(accounts.findByMemberIdForUpdate(id)).thenReturn(Optional.of(account));
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        // when
        assertThat(service.withdraw(id, reauth)).isInstanceOf(WithdrawalOutcome.Withdrawn.class);
        // then
        var order = inOrder(accounts, verification, tokens, publisher);
        order.verify(accounts).findByMemberIdForUpdate(id);
        order.verify(verification).consumeNonce(reauth);
        order.verify(accounts)
                .save(argThat(a -> a.isWithdrawn() && a.withdrawnAt().equals(now)));
        order.verify(tokens).revokeAllActiveByMember(id, now);
        order.verify(publisher).publishEvent(new MemberWithdrawn(id, now));
    }

    @Test
    @DisplayName("이미 탈퇴했으면 재인증과 이벤트 없이 성공한다")
    void idempotent() {
        // given
        when(accounts.findByMemberIdForUpdate(id)).thenReturn(Optional.of(account.withdraw(clock.instant())));
        // when / then
        assertThat(service.withdraw(id, null)).isInstanceOf(WithdrawalOutcome.AlreadyWithdrawn.class);
        verifyNoInteractions(verification, tokens, publisher);
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("다른 회원의 소셜 연결은 변경과 nonce 소모 없이 거절한다")
    void rejectsOtherIdentity() {
        // given
        when(accounts.findByMemberIdForUpdate(id)).thenReturn(Optional.of(account));
        // when / then
        assertThatThrownBy(() -> service.withdraw(id, new VerifiedSocialIdentity(AuthProvider.KAKAO, "other", "nonce")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.REAUTHENTICATION_FAILED));
        verifyNoInteractions(verification, tokens, publisher);
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("계정 부재와 제재는 재인증보다 먼저 거절한다")
    void rejectsMissingAndSanctioned() {
        // given / when / then
        assertThatThrownBy(() -> service.withdraw(id, null))
                .isInstanceOfSatisfying(
                        BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.UNAUTHORIZED));
        when(accounts.findByMemberIdForUpdate(id)).thenReturn(Optional.of(account.sanction(null)));
        assertThatThrownBy(() -> service.withdraw(id, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.ACCOUNT_SANCTIONED));
        verifyNoInteractions(verification, tokens, publisher);
    }

    @Test
    @DisplayName("nonce 소모 실패 예외는 그대로 전달하고 저장하지 않는다")
    void propagatesNonceFailure() {
        // given
        when(accounts.findByMemberIdForUpdate(id)).thenReturn(Optional.of(account));
        var failure = new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        doThrow(failure).when(verification).consumeNonce(reauth);
        // when / then
        assertThatThrownBy(() -> service.withdraw(id, reauth)).isSameAs(failure);
        verify(accounts, never()).save(any());
        verifyNoInteractions(tokens, publisher);
    }
}
