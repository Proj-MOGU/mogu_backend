package com.mogu.auth.application;

public sealed interface LoginResult {
    record LoggedIn(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) implements LoginResult {}

    record RegistrationRequired(IssuedRegistrationToken registrationToken) implements LoginResult {}
}
