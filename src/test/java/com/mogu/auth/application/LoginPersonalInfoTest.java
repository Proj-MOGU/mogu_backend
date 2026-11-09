package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.DiHash;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.auth.MemberId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoginPersonalInfoTest {
    private final SocialTokenVerificationService verification = mock(SocialTokenVerificationService.class);
    private final AuthAccountRepository accounts = mock(AuthAccountRepository.class);
    private final RegistrationTokenIssuer registrations = mock(RegistrationTokenIssuer.class);
    private final LoginCompletionService service = new LoginCompletionService(
            verification,
            accounts,
            registrations,
            mock(AccessTokenIssuer.class),
            mock(RefreshTokenIssuer.class),
            Clock.systemUTC());
    private final SocialIdentity social = new SocialIdentity(AuthProvider.KAKAO, "subject");

    private AuthAccount account() {
        return AuthAccount.register(new MemberId(UUID.randomUUID()), new DiHash("a".repeat(64)), social, Instant.now())
                .updatePersonalInfo("홍길동", "01012345678")
                .updateSocialEmail(social, "old@kakao.com", false);
    }

    @Test
    @DisplayName("연결된 계정으로 로그인하면 이메일만 갱신하고 본인인증 정보는 유지한다")
    void updatesEmailOnLogin() {
        // given
        var account = account();
        when(accounts.findBySocialIdentity(social)).thenReturn(Optional.of(account));
        // when
        service.complete(new VerifiedSocialIdentity(AuthProvider.KAKAO, "subject", "nonce", "new@kakao.com", true));
        // then
        var saved = ArgumentCaptor.forClass(AuthAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().name()).isEqualTo("홍길동");
        assertThat(saved.getValue().phoneNumber()).isEqualTo("01012345678");
        assertThat(saved.getValue().socialIdentities().getFirst().email()).isEqualTo("new@kakao.com");
        assertThat(saved.getValue().socialIdentities().getFirst().emailVerified())
                .isTrue();
    }

    @Test
    @DisplayName("이메일 없는 로그인은 기존 값을 덮어쓰지 않는다")
    void doesNotWriteAbsentEmail() {
        // given
        when(accounts.findBySocialIdentity(social)).thenReturn(Optional.of(account()));
        // when
        service.complete(new VerifiedSocialIdentity(AuthProvider.KAKAO, "subject", "nonce", null, false));
        // then
        verify(accounts, never()).save(any());
    }

    @Test
    @DisplayName("제재 또는 가입 대기 결과는 계정 개인정보를 저장하지 않는다")
    void doesNotWriteForSanctionOrPending() {
        // given
        var identity = new VerifiedSocialIdentity(AuthProvider.KAKAO, "subject", "nonce", "new@kakao.com", true);
        when(accounts.findBySocialIdentity(social))
                .thenReturn(Optional.of(account().sanction(null)));
        // when / then
        assertThat(service.complete(identity)).isInstanceOf(LoginOutcome.Sanctioned.class);
        when(accounts.findBySocialIdentity(social)).thenReturn(Optional.empty());
        assertThat(service.complete(identity)).isInstanceOf(LoginOutcome.RegistrationRequired.class);
        verify(registrations).issue(social, "new@kakao.com", true);
        verify(accounts, never()).save(any());
    }
}
