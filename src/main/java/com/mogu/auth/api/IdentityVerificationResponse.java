package com.mogu.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mogu.auth.application.RegistrationOutcome;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityVerificationResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        boolean newMember) {
    public static IdentityVerificationResponse from(RegistrationOutcome.Completed result) {
        return new IdentityVerificationResponse(
                result.accessToken().value(),
                result.accessToken().expiresAt(),
                result.refreshToken().value(),
                result.refreshToken().expiresAt(),
                result.newMember());
    }

    @Override
    public String toString() {
        return "IdentityVerificationResponse[accessToken=***, refreshToken=***, newMember=" + newMember + "]";
    }
}
