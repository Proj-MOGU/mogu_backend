package com.mogu.auth.api;

import com.mogu.auth.application.RefreshOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RefreshResponse(
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
        Instant refreshTokenExpiresAt) {
    public static RefreshResponse from(RefreshOutcome.Rotated result) {
        return new RefreshResponse(
                result.accessToken().value(),
                result.accessToken().expiresAt(),
                result.refreshToken().value(),
                result.refreshToken().expiresAt());
    }

    @Override
    public String toString() {
        return "RefreshResponse[accessToken=***, refreshToken=***]";
    }
}
