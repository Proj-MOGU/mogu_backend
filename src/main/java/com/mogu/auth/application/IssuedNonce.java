package com.mogu.auth.application;

import java.time.Instant;

public record IssuedNonce(String value, Instant expiresAt) {
    @Override
    public String toString() {
        return "IssuedNonce[value=***, expiresAt=" + expiresAt + "]";
    }
}
