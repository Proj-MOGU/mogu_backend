package com.mogu.auth.application;

public sealed interface LoginOutcome {
    record LoggedIn(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) implements LoginOutcome {}

    record RegistrationRequired(IssuedRegistrationToken registrationToken) implements LoginOutcome {}

    record Sanctioned() implements LoginOutcome {}
}
