package com.mogu.auth.infrastructure;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.auth.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration clockSkew,
        boolean ephemeralKeyAllowed,
        SigningKey signingKey,
        List<VerificationKey> verificationKeys) {
    public JwtProperties {
        verificationKeys = verificationKeys == null ? List.of() : List.copyOf(verificationKeys);
    }

    public record SigningKey(String keyId, String privateKeyPem, String publicKeyPem) {
        @Override
        public String toString() {
            return "SigningKey[keyId=" + keyId + ", privateKeyPem=***, publicKeyPem=***]";
        }
    }

    public record VerificationKey(String keyId, String publicKeyPem) {}
}
