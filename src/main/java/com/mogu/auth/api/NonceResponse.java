package com.mogu.auth.api;

import java.time.Instant;

public record NonceResponse(String nonce, Instant expiresAt) {
    @Override
    public String toString() {
        return "NonceResponse[nonce=***, expiresAt=" + expiresAt + "]";
    }
}
