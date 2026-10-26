package com.mogu.auth.infrastructure;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

final class JwtSigningKey {
    private final ECKey key;

    JwtSigningKey(ECKey key) {
        this.key = key;
    }

    String keyId() {
        return key.getKeyID();
    }

    JWK publicJwk() {
        return key.toPublicJWK();
    }

    JWKSet signingJwkSet() {
        return new JWKSet(key);
    }

    @Override
    public String toString() {
        return "JwtSigningKey[keyId=" + keyId() + "]";
    }
}
