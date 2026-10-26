package com.mogu.auth.application;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {
    @Override
    public String toString() {
        return "IssuedAccessToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
