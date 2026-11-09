package com.mogu.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mogu.auth.application.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        @Schema(description = "본인인증을 통한 가입 완료 필요 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean registrationRequired,

        @Schema(
                description = "Access Token. registrationRequired가 false일 때만 포함",
                example = "eyJ...",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String accessToken,

        @Schema(
                description = "Access Token 만료 시각. registrationRequired가 false일 때만 포함",
                example = "2026-01-01T00:15:00Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant accessTokenExpiresAt,

        @Schema(
                description = "Refresh Token. registrationRequired가 false일 때만 포함",
                example = "refresh-token-placeholder",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String refreshToken,

        @Schema(
                description = "Refresh Token 만료 시각. registrationRequired가 false일 때만 포함",
                example = "2026-01-31T00:00:00Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant refreshTokenExpiresAt,

        @Schema(
                description = "가입 대기 Registration Token. registrationRequired가 true일 때만 포함",
                example = "eyJ...",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String registrationToken,

        @Schema(
                description = "Registration Token 만료 시각. registrationRequired가 true일 때만 포함",
                example = "2026-01-01T00:10:00Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant registrationTokenExpiresAt) {
    public static LoginResponse from(LoginResult result) {
        return switch (result) {
            case LoginResult.LoggedIn r ->
                new LoginResponse(
                        false,
                        r.accessToken().value(),
                        r.accessToken().expiresAt(),
                        r.refreshToken().value(),
                        r.refreshToken().expiresAt(),
                        null,
                        null);
            case LoginResult.RegistrationRequired r ->
                new LoginResponse(
                        true,
                        null,
                        null,
                        null,
                        null,
                        r.registrationToken().value(),
                        r.registrationToken().expiresAt());
        };
    }

    @Override
    public String toString() {
        return "LoginResponse[registrationRequired=" + registrationRequired
                + ", accessToken=***, refreshToken=***, registrationToken=***]";
    }
}
