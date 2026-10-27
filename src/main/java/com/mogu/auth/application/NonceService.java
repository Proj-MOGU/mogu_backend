package com.mogu.auth.application;

import org.springframework.stereotype.Service;

@Service
public class NonceService {
    private final NonceCodec codec;

    public NonceService(NonceCodec codec) {
        this.codec = codec;
    }

    public IssuedNonce issue() {
        return codec.issue();
    }
}
