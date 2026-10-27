package com.mogu.auth.application;

import java.time.Instant;

public record IssuedRegistrationToken(String value, Instant expiresAt) {
    @Override
    public String toString() {
        return "IssuedRegistrationToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
