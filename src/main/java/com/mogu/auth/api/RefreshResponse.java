package com.mogu.auth.api;

import com.mogu.auth.application.RefreshOutcome;
import java.time.Instant;

public record RefreshResponse(
        String accessToken, Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {
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
