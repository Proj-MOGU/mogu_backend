package com.mogu.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record LogoutRequest(
        @Schema(
                description = "Refresh Token",
                example = "refresh-token-placeholder",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String refreshToken) {
    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=***]";
    }
}
