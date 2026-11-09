package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.DiHash;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class IdentityPersonalInfoTest {
    private final Instant now = Instant.parse("2026-11-09T00:00:00Z");
    private final UUID registrationId = UUID.randomUUID();
    private final SocialIdentity social = new SocialIdentity(AuthProvider.KAKAO, "subject");
    private final IdentityVerificationAdapter adapter = mock(IdentityVerificationAdapter.class);
    private final DiHasher hasher = mock(DiHasher.class);
    private final RegistrationCompletionService completion = mock(RegistrationCompletionService.class);

    private IdentityVerificationService service() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("adapter", adapter);
        return new IdentityVerificationService(
                beans.getBeanProvider(IdentityVerificationAdapter.class),
                hasher,
                completion,
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(10));
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "NULL,01012345678",
                "'',01012345678",
                "홍길동,NULL",
                "홍길동,''",
                "홍길동,123456789",
                "홍길동,123456789012",
                "홍길동,010a2345678"
            },
            nullValues = "NULL")
    @DisplayName("본명이나 번호가 비어 있거나 번호 형식이 잘못되면 인증을 거부한다")
    void rejectsInvalidPersonalInfo(String name, String phone) {
        // given
        when(adapter.fetch("verification"))
                .thenReturn(new VerifiedIdentity(
                        "verification", registrationId, "di", now, IdentitySource.MOCK, name, phone));
        // when / then
        assertThatThrownBy(() -> service().verify(registrationId, social, "verification", null, false))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.errorCode())
                                .isEqualTo(AuthErrorCode.INVALID_IDENTITY_VERIFICATION));
        verifyNoInteractions(completion, hasher);
    }

    @Test
    @DisplayName("하이픈을 제거한 번호와 이번 본인인증 이름을 가입 완료에 전달한다")
    void normalizesPhone() {
        // given
        var hash = new DiHash("a".repeat(64));
        when(adapter.fetch("verification"))
                .thenReturn(new VerifiedIdentity(
                        "verification", registrationId, "di", now, IdentitySource.MOCK, "홍길동", "010-1234-5678"));
        when(hasher.hash("di")).thenReturn(hash);
        when(completion.complete("verification", hash, social, "홍길동", "01012345678", "hong@kakao.com", true))
                .thenReturn(new RegistrationOutcome.Completed(null, null, true));
        // when
        service().verify(registrationId, social, "verification", "hong@kakao.com", true);
        // then
        verify(completion).complete("verification", hash, social, "홍길동", "01012345678", "hong@kakao.com", true);
    }
}
