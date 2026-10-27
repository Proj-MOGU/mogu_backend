package com.mogu.auth.infrastructure;

import com.mogu.auth.application.IssuedRegistrationToken;
import com.mogu.auth.application.RegistrationTokenIssuer;
import com.mogu.auth.domain.SocialIdentity;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtRegistrationTokenIssuer implements RegistrationTokenIssuer {
    private final JwtEncoder encoder;
    private final RegistrationJwtProperties properties;
    private final Clock clock;
    private final String keyId;

    JwtRegistrationTokenIssuer(
            @Qualifier("registrationJwtEncoder") JwtEncoder encoder,
            RegistrationJwtProperties properties,
            @Qualifier("authClock") Clock clock,
            @Qualifier("registrationSigningKey") JwtSigningKey signingKey) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.keyId = signingKey.keyId();
    }

    @Override
    public IssuedRegistrationToken issue(SocialIdentity identity) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var expiresAt = now.plus(properties.ttl());
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(UUID.randomUUID().toString())
                .claim("provider", identity.provider().name())
                .claim("provider_sub", identity.subject())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.ES256)
                .keyId(keyId)
                .type("registration+jwt")
                .build();
        return new IssuedRegistrationToken(
                encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }
}
