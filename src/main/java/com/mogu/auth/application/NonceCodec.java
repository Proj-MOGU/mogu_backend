package com.mogu.auth.application;

import java.time.Instant;

public interface NonceCodec {
    IssuedNonce issue();

    Instant verifyAndGetExpiry(String nonce);
}
