package com.mogu.auth.application;

public sealed interface RegistrationOutcome {
    record Completed(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken, boolean newMember)
            implements RegistrationOutcome {}

    record Sanctioned() implements RegistrationOutcome {}
}
