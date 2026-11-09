package com.mogu.auth.application;

public sealed interface RefreshOutcome {
    record Rotated(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) implements RefreshOutcome {}

    record Invalid() implements RefreshOutcome {}
}
