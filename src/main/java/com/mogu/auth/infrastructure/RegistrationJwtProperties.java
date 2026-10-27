package com.mogu.auth.infrastructure;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.auth.registration-jwt")
public record RegistrationJwtProperties(
        String issuer,
        String audience,
        Duration ttl,
        Duration clockSkew,
        boolean ephemeralKeyAllowed,
        JwtProperties.SigningKey signingKey,
        List<JwtProperties.VerificationKey> verificationKeys) {
    public RegistrationJwtProperties {
        issuer = issuer == null ? "mogu" : issuer;
        audience = audience == null ? "mogu-registration" : audience;
        ttl = ttl == null ? Duration.ofMinutes(10) : ttl;
        clockSkew = clockSkew == null ? Duration.ofSeconds(60) : clockSkew;
        verificationKeys = verificationKeys == null ? List.of() : List.copyOf(verificationKeys);
        if (ttl.isNegative() || ttl.isZero() || clockSkew.isNegative())
            throw new IllegalArgumentException("mogu.auth.registration-jwt timing");
    }
}
