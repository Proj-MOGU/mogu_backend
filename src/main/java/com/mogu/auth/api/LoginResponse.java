package com.mogu.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mogu.auth.application.LoginResult;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        boolean registrationRequired,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String registrationToken,
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
