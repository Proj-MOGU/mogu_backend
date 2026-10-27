package com.mogu.auth.infrastructure;

import com.mogu.auth.application.AccessTokenIssuer;
import com.mogu.auth.application.IssuedAccessToken;
import com.mogu.common.auth.MemberId;
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
public class JwtAccessTokenIssuer implements AccessTokenIssuer {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;
    private final String keyId;

    JwtAccessTokenIssuer(
            @Qualifier("jwtEncoder") JwtEncoder encoder,
            JwtProperties properties,
            @Qualifier("authClock") Clock clock,
            @Qualifier("signingKey") JwtSigningKey signingKey) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.keyId = signingKey.keyId();
    }

    @Override
    public IssuedAccessToken issue(MemberId memberId) {
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var expiresAt = now.plus(properties.accessTokenTtl());
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(memberId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.ES256)
                .keyId(keyId)
                .type("at+jwt")
                .build();
        return new IssuedAccessToken(
                encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }
}
