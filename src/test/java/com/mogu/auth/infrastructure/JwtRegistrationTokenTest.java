package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.SocialIdentity;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtRegistrationTokenTest {
    private final Instant now = Instant.parse("2026-10-27T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final RegistrationJwtProperties properties =
            new RegistrationJwtProperties(null, null, null, null, false, null, List.of());
    private JwtSigningKey key;
    private JwtRegistrationTokenIssuer issuer;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        key = new JwtSigningKey(
                new ECKeyGenerator(Curve.P_256).keyID("registration").generate());
        var config = new RegistrationKeyConfig();
        issuer = new JwtRegistrationTokenIssuer(config.registrationJwtEncoder(key), properties, clock, key);
        decoder = config.registrationJwtDecoder(key, properties, clock);
    }

    @Test
    @DisplayName("가입 대기 토큰의 헤더와 클레임 및 기본 만료 시간을 검증한다")
    void issuedTokenMatchesContract() {
        // given
        var identity = new SocialIdentity(AuthProvider.GOOGLE, "subject");
        // when
        var issued = issuer.issue(identity);
        var jwt = decoder.decode(issued.value());
        var next = decoder.decode(issuer.issue(identity).value());
        // then
        assertThat(jwt.getHeaders())
                .containsEntry("alg", "ES256")
                .containsEntry("typ", "registration+jwt")
                .containsEntry("kid", "registration");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("mogu");
        assertThat(jwt.getAudience()).containsExactly("mogu-registration");
        assertThat(UUID.fromString(jwt.getSubject())).isNotNull();
        assertThat(UUID.fromString(jwt.getId())).isNotNull();
        assertThat(next.getSubject()).isNotEqualTo(jwt.getSubject());
        assertThat(next.getId()).isNotEqualTo(jwt.getId());
        assertThat(jwt.getClaimAsString("provider")).isEqualTo("GOOGLE");
        assertThat(jwt.getClaimAsString("provider_sub")).isEqualTo("subject");
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plusSeconds(600)).isEqualTo(issued.expiresAt());
        assertThat(jwt.getClaims())
                .containsOnlyKeys("iss", "aud", "sub", "iat", "exp", "jti", "provider", "provider_sub");
        assertThat(issued.toString()).contains("***").doesNotContain(issued.value());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "expired",
                "accessTypeAndKey",
                "accessKey",
                "audience",
                "provider",
                "missingProviderSubject",
                "blankProviderSubject",
                "missingExpiration",
                "invalidSubject",
                "missingKid",
                "missingType",
                "uppercaseType",
                "issuer"
            })
    @DisplayName("위조 키와 헤더 및 잘못된 가입 대기 클레임을 거부한다")
    void rejectsInvalidToken(String scenario) throws Exception {
        // given
        var original = SignedJWT.parse(
                issuer.issue(new SocialIdentity(AuthProvider.KAKAO, "subject")).value());
        var claims = new JWTClaimsSet.Builder(original.getJWTClaimsSet());
        var header = new JWSHeader.Builder(original.getHeader());
        var signingKey = key.signingJwkSet().getKeys().getFirst().toECKey();
        switch (scenario) {
            case "expired" ->
                claims.issueTime(Date.from(now.minusSeconds(700))).expirationTime(Date.from(now.minusSeconds(61)));
            case "accessTypeAndKey", "accessKey" -> {
                signingKey = new ECKeyGenerator(Curve.P_256).keyID("access").generate();
                header.keyID("access");
                if (scenario.equals("accessTypeAndKey")) header.type(new JOSEObjectType("at+jwt"));
            }
            case "audience" -> claims.audience("other");
            case "provider" -> claims.claim("provider", "APPLE");
            case "missingProviderSubject" -> claims.claim("provider_sub", null);
            case "blankProviderSubject" -> claims.claim("provider_sub", " ");
            case "missingExpiration" -> claims.expirationTime(null);
            case "invalidSubject" -> claims.subject("invalid");
            case "missingKid" -> header.keyID(null);
            case "missingType" -> header.type(null);
            case "uppercaseType" -> header.type(new JOSEObjectType("REGISTRATION+JWT"));
            case "issuer" -> claims.issuer("other");
            default -> throw new IllegalArgumentException("Unknown scenario");
        }
        var token = new SignedJWT(header.build(), claims.build());
        token.sign(new ECDSASigner(signingKey));
        // when / then
        assertThatThrownBy(() -> decoder.decode(token.serialize())).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료 후 정확히 60초까지 허용하고 그 이후에는 거부한다")
    void expirationSkewBoundary() {
        // given
        var issued = issuer.issue(new SocialIdentity(AuthProvider.KAKAO, "subject"));
        var config = new RegistrationKeyConfig();
        var boundary =
                config.registrationJwtDecoder(key, properties, Clock.fixed(now.plusSeconds(660), ZoneOffset.UTC));
        var after = config.registrationJwtDecoder(key, properties, Clock.fixed(now.plusSeconds(661), ZoneOffset.UTC));
        // when / then
        assertThat(boundary.decode(issued.value()).getExpiresAt()).isEqualTo(issued.expiresAt());
        assertThatThrownBy(() -> after.decode(issued.value())).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("가입 대기 토큰을 Access Token 디코더에서 거부한다")
    void accessDecoderRejectsRegistration() throws Exception {
        // given
        var accessKey = new JwtSigningKey(
                new ECKeyGenerator(Curve.P_256).keyID("access").generate());
        var accessProperties = new JwtProperties(
                "mogu", "mogu-api", Duration.ofMinutes(15), Duration.ofSeconds(60), false, null, List.of());
        var accessDecoder = new JwtKeyConfig().jwtDecoder(accessKey, accessProperties, clock);
        var issued = issuer.issue(new SocialIdentity(AuthProvider.KAKAO, "subject"));
        // when / then
        assertThatThrownBy(() -> accessDecoder.decode(issued.value())).isInstanceOf(JwtException.class);
    }
}
