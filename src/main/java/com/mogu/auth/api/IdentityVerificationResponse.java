package com.mogu.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mogu.auth.application.RegistrationOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityVerificationResponse(
        @Schema(description = "Access Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,

        @Schema(
                description = "Access Token 만료 시각",
                example = "2026-01-01T00:15:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant accessTokenExpiresAt,

        @Schema(
                description = "Refresh Token",
                example = "refresh-token-placeholder",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken,

        @Schema(
                description = "Refresh Token 만료 시각",
                example = "2026-01-31T00:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant refreshTokenExpiresAt,

        @Schema(
                description = "신규 회원 여부. true이면 프로필 입력 필요",
                example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
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
