package com.mogu.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record NonceResponse(
        @Schema(
                description = "소셜 로그인 재사용 방지용 nonce",
                example = "nonce-placeholder",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String nonce,

        @Schema(
                description = "nonce 만료 시각",
                example = "2026-01-01T00:05:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt) {
    @Override
    public String toString() {
        return "NonceResponse[nonce=***, expiresAt=" + expiresAt + "]";
    }
}
