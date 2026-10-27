package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.UsedNonceRepository;
import com.mogu.common.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialTokenVerificationServiceUnitTest {
    @Test
    @DisplayName("같은 공급자 어댑터를 중복 등록하면 기동 실패한다")
    void rejectsDuplicateAdapters() {
        // given
        var adapter = mock(SocialLoginAdapter.class);
        when(adapter.provider()).thenReturn(AuthProvider.KAKAO);
        // when / then
        assertThatThrownBy(() -> new SocialTokenVerificationService(
                        List.of(adapter, adapter),
                        mock(NonceCodec.class),
                        mock(UsedNonceRepository.class),
                        Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("등록되지 않은 공급자는 지원하지 않는 방식이다")
    void rejectsAbsentAdapter() {
        // given
        var service = new SocialTokenVerificationService(
                List.of(), mock(NonceCodec.class), mock(UsedNonceRepository.class), Clock.systemUTC());
        // when / then
        assertThatThrownBy(() -> service.verify(AuthProvider.KAKAO, "unused"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.UNSUPPORTED_PROVIDER));
    }

    @Test
    @DisplayName("정리는 현재 시각에서 하루 이전의 만료 기록을 대상으로 한다")
    void cleanupRetainsOneDay() {
        // given
        var now = Instant.parse("2026-10-27T00:00:00Z");
        var repository = mock(UsedNonceRepository.class);
        var cleanup = new UsedNonceCleanup(repository, Clock.fixed(now, ZoneOffset.UTC));
        // when
        cleanup.cleanup();
        // then
        verify(repository).deleteExpiredBefore(now.minusSeconds(86400));
    }

    @Test
    @DisplayName("검증 결과의 문자열에서 nonce를 숨긴다")
    void redactsNonce() {
        // given
        var identity = new VerifiedSocialIdentity(AuthProvider.KAKAO, "subject", "sensitive-nonce");
        // when / then
        assertThat(identity.toString()).doesNotContain(identity.nonce());
    }
}
