package com.mogu.auth.application;

import java.time.Instant;

public record IssuedRefreshToken(String value, Instant expiresAt) {
    @Override
    public String toString() {
        return "IssuedRefreshToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
