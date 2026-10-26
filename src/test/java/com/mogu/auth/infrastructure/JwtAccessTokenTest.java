package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.auth.MemberId;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtAccessTokenTest {
    private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final JwtProperties properties = new JwtProperties(
            "mogu", "mogu-api", Duration.ofMinutes(15), Duration.ofSeconds(60), false, null, List.of());
    private ECKey key;
    private JwtAccessTokenIssuer issuer;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        key = new ECKey.Builder(Curve.P_256, (ECPublicKey) pair.getPublic())
                .privateKey((ECPrivateKey) pair.getPrivate())
                .keyID("current")
                .build();
        var signingKey = new JwtSigningKey(key);
        issuer = new JwtAccessTokenIssuer(new JwtKeyConfig().jwtEncoder(signingKey), properties, clock, signingKey);
        decoder = JwtKeyConfig.createDecoder(new JWKSet(key.toPublicJWK()), properties, clock);
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer("mogu")
                .audience("mogu-api")
                .subject("00000000-0000-7000-8000-000000000001")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString());
    }

    private JWSHeader.Builder header() {
        return new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("current").type(new JOSEObjectType("at+jwt"));
    }

    private String sign(JWSHeader header, JWTClaimsSet claims, ECKey key) throws Exception {
        var jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }

    private String sign(JWTClaimsSet claims) throws Exception {
        return sign(header().build(), claims, key);
    }

    private JwtDecoder at(Instant instant) {
        return JwtKeyConfig.createDecoder(
                new JWKSet(key.toPublicJWK()), properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("발급 토큰의 모든 클레임과 헤더가 명세를 따른다")
    void issuedClaimsMatchSpecification() {
        // given
        var member = MemberId.from("00000000-0000-7000-8000-000000000001");
        // when
        var issued = issuer.issue(member);
        var jwt = decoder.decode(issued.value());
        // then
        assertThat(jwt.getSubject()).isEqualTo(member.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("mogu");
        assertThat(jwt.getAudience()).containsExactly("mogu-api");
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plusSeconds(900)).isEqualTo(issued.expiresAt());
        assertThatCode(() -> UUID.fromString(jwt.getId())).doesNotThrowAnyException();
        assertThat(jwt.getHeaders())
                .containsEntry("alg", "ES256")
                .containsEntry("kid", "current")
                .containsEntry("typ", "at+jwt");
        assertThat(jwt.getClaims()).containsOnlyKeys("sub", "iss", "aud", "iat", "exp", "jti");
        assertThat(decoder.decode(issuer.issue(member).value()).getId()).isNotEqualTo(jwt.getId());
    }

    @Test
    @DisplayName("만료 허용 오차를 초과하면 거부한다")
    void expiredBeyondSkewIsRejected() throws Exception {
        // given
        var token = sign(claims().build());
        var later = at(now.plusSeconds(961));
        // when / then
        assertThatThrownBy(() -> later.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료 후 정확히 60초까지 허용한다")
    void expirationAtSkewBoundaryIsAccepted() throws Exception {
        // given
        var token = sign(claims().build());
        var later = at(now.plusSeconds(960));
        // when / then
        assertThatCode(() -> later.decode(token)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("서명 한 바이트 변조를 거부한다")
    void alteredSignatureIsRejected() throws Exception {
        // given
        var token = SignedJWT.parse(sign(claims().build()));
        byte[] signature = token.getSignature().decode();
        signature[0] ^= 1;
        var altered = token.getHeader().toBase64URL() + "." + token.getPayload().toBase64URL() + "."
                + Base64URL.encode(signature);
        // when / then
        assertThatThrownBy(() -> decoder.decode(altered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("서명 없는 토큰을 거부한다")
    void noneAlgorithmIsRejected() {
        // given
        var token = new PlainJWT(claims().build()).serialize();
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("HS256 토큰을 거부한다")
    void hs256IsRejected() throws Exception {
        // given
        var token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .type(new JOSEObjectType("at+jwt"))
                        .keyID("current")
                        .build(),
                claims().build());
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        token.sign(new MACSigner(secret));
        // when / then
        assertThatThrownBy(() -> decoder.decode(token.serialize())).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("RS256 토큰을 거부한다")
    void rs256IsRejected() throws Exception {
        // given
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(new JOSEObjectType("at+jwt"))
                        .keyID("current")
                        .build(),
                claims().build());
        token.sign(new RSASSASigner(generator.generateKeyPair().getPrivate()));
        // when / then
        assertThatThrownBy(() -> decoder.decode(token.serialize())).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("같은 키 ID를 가진 다른 키의 서명을 거부한다")
    void wrongSigningKeyIsRejected() throws Exception {
        // given
        var other = new ECKeyGenerator(Curve.P_256).generate();
        var token = sign(header().build(), claims().build(), other);
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("모르는 키 ID를 거부한다")
    void unknownKidIsRejected() throws Exception {
        // given
        var token = sign(header().keyID("unknown").build(), claims().build(), key);
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("키 ID가 없으면 거부한다")
    void missingKidIsRejected() throws Exception {
        // given
        var token = sign(header().keyID(null).build(), claims().build(), key);
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("JWT 타입을 거부한다")
    void jwtTypeIsRejected() throws Exception {
        // given
        var token = sign(header().type(JOSEObjectType.JWT).build(), claims().build(), key);
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("타입이 없으면 거부한다")
    void missingTypeIsRejected() throws Exception {
        // given
        var token = sign(header().type(null).build(), claims().build(), key);
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("대소문자가 다른 타입을 거부한다")
    void uppercaseTypeIsRejected() throws Exception {
        // given
        var token = sign(header().type(new JOSEObjectType("AT+JWT")).build(), claims().build(), key);
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("다른 발급자를 거부한다")
    void wrongIssuerIsRejected() throws Exception {
        // given
        var token = sign(claims().issuer("other").build());
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("다른 수신자를 거부한다")
    void wrongAudienceIsRejected() throws Exception {
        // given
        var token = sign(claims().audience("other").build());
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("수신자가 없으면 거부한다")
    void missingAudienceIsRejected() throws Exception {
        // given
        var token = sign(claims().audience((String) null).build());
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("UUID가 아닌 회원 ID를 거부한다")
    void invalidSubjectIsRejected() throws Exception {
        // given
        var token = sign(claims().subject("1").build());
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("회원 ID가 없으면 거부한다")
    void missingSubjectIsRejected() throws Exception {
        // given
        var token = sign(claims().subject(null).build());
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("만료 시각이 없으면 거부한다")
    void missingExpirationIsRejected() throws Exception {
        // given
        var token = sign(claims().expirationTime(null).build());
        // when / then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("키 교체 시 이전 공개키를 등록한 경우에만 이전 토큰을 허용한다")
    void rotationRequiresPreviousVerificationKey() throws Exception {
        // given
        var token = sign(claims().build());
        var next = new ECKeyGenerator(Curve.P_256).keyID("next").generate();
        var previous = new JwtProperties.VerificationKey(
                key.getKeyID(), pem("PUBLIC KEY", key.toECPublicKey().getEncoded()));
        var rotating = new JwtProperties(
                "mogu", "mogu-api", Duration.ofMinutes(15), Duration.ofSeconds(60), false, null, List.of(previous));
        // when
        var retained = new JwtKeyConfig().jwtDecoder(new JwtSigningKey(next), rotating, clock);
        var removed = new JwtKeyConfig().jwtDecoder(new JwtSigningKey(next), properties, clock);
        // then
        assertThatCode(() -> retained.decode(token)).doesNotThrowAnyException();
        assertThatThrownBy(() -> removed.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("토큰 객체 문자열에 토큰 원문을 노출하지 않는다")
    void tokenStringIsRedacted() {
        // given
        var token = issuer.issue(MemberId.from("00000000-0000-7000-8000-000000000001"));
        // when / then
        assertThat(token.toString()).doesNotContain(token.value()).contains("***");
    }

    static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(bytes) + "\n-----END " + type + "-----";
    }
}
