package com.mogu.auth.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.auth.nonce")
public record NonceProperties(String secret, boolean ephemeralSecretAllowed, Duration ttl) {
    public NonceProperties {
        ttl = ttl == null ? Duration.ofMinutes(5) : ttl;
        if (ttl.isNegative() || ttl.isZero() || ttl.getNano() != 0)
            throw new IllegalArgumentException("mogu.auth.nonce.ttl");
    }

    @Override
    public String toString() {
        return "NonceProperties[secret=***, ephemeralSecretAllowed=" + ephemeralSecretAllowed + ", ttl=" + ttl + "]";
    }
}
