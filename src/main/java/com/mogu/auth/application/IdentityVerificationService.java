package com.mogu.auth.application;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IdentityVerificationService {
    private final ObjectProvider<IdentityVerificationAdapter> adapters;
    private final DiHasher diHasher;
    private final RegistrationCompletionService completion;
    private final Clock clock;
    private final Duration maxAge;

    public IdentityVerificationService(
            ObjectProvider<IdentityVerificationAdapter> adapters,
            DiHasher diHasher,
            RegistrationCompletionService completion,
            @Qualifier("authClock") Clock clock,
            @Value("${mogu.auth.identity.max-age:10m}") Duration maxAge) {
        this.adapters = adapters;
        this.diHasher = diHasher;
        this.completion = completion;
        this.clock = clock;
        this.maxAge = maxAge;
        if (maxAge.isNegative() || maxAge.isZero()) throw new IllegalArgumentException("mogu.auth.identity.max-age");
    }

    public RegistrationOutcome.Completed verify(UUID registrationId, SocialIdentity social, String verificationId) {
        // 실제 업체가 등록되기 전에도 운영 애플리케이션은 기동하며 인증 요청은 실패 처리한다.
        var adapter = adapters.getIfAvailable();
        if (adapter == null) throw invalid();
        var verified = adapter.fetch(verificationId);
        var now = clock.instant();
        if (verified == null
                || !verificationId.equals(verified.verificationId())
                || !registrationId.equals(verified.registrationId())
                || verified.verifiedAt() == null
                || verified.verifiedAt().isBefore(now.minus(maxAge))
                || verified.verifiedAt().isAfter(now)
                || verified.di() == null
                || verified.di().isBlank()
                || verified.source() == null) throw invalid();
        return switch (completion.complete(verificationId, diHasher.hash(verified.di()), social)) {
            case RegistrationOutcome.Completed result -> result;
            case RegistrationOutcome.Sanctioned ignored ->
                throw new BusinessException(AuthErrorCode.ACCOUNT_SANCTIONED);
        };
    }

    private static BusinessException invalid() {
        return new BusinessException(AuthErrorCode.INVALID_IDENTITY_VERIFICATION);
    }
}
