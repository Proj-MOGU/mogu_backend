package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.support.FakeOidcProvider;
import com.mogu.common.error.BusinessException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtException;

class OidcSocialLoginAdapterTest {
    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
    private final FakeOidcProvider fake = new FakeOidcProvider();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @AfterEach
    void close() {
        fake.close();
    }

    private OidcProviderProperties properties(List<String> audiences) {
        var provider = new OidcProviderProperties.Provider(
                List.of("https://accounts.google.com", "accounts.google.com"),
                fake.jwkSetUri(),
                audiences,
                Duration.ofSeconds(1),
                Duration.ofMillis(300));
        return new OidcProviderProperties(provider, provider);
    }

    private KakaoLoginAdapter adapter() {
        return new KakaoLoginAdapter(properties(List.of("client")), clock, new MockEnvironment());
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience("client")
                .subject("subject")
                .claim("nonce", "nonce")
                .issueTime(Date.from(NOW.minusSeconds(60)))
                .expirationTime(Date.from(NOW.plusSeconds(300)));
    }

    private void rejected(OidcSocialLoginAdapter adapter, String token, AuthErrorCode error) {
        assertThatThrownBy(() -> adapter.verify(token))
                .isInstanceOfSatisfying(
                        BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(error));
    }

    @Test
    @DisplayName("기동 시 조회하지 않고 정상 서명과 필수 클레임을 검증한다")
    void validTokenAndLazyFetch() throws Exception {
        // given
        var adapter = adapter();
        assertThat(fake.requests()).isZero();
        var token = fake.sign(claims().build());
        // when
        var identity = adapter.verify(token);
        // then
        assertThat(identity.provider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(identity.subject()).isEqualTo("subject");
        assertThat(identity.nonce()).isEqualTo("nonce");
        assertThat(fake.requests()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "issuer",
                "audience",
                "missingAudience",
                "missingIssuer",
                "expired",
                "subject",
                "nonce",
                "emptyNonce",
                "blankNonce",
                "iat",
                "exp",
                "numericSubject",
                "numericNonce"
            })
    @DisplayName("잘못되거나 누락된 클레임은 인증 실패다")
    void invalidClaims(String kind) throws Exception {
        // given
        var claims = claims();
        switch (kind) {
            case "issuer" -> claims.issuer("https://evil.example");
            case "missingIssuer" -> claims.issuer(null);
            case "audience" -> claims.audience("other");
            case "missingAudience" -> claims.claim("aud", null);
            case "expired" ->
                claims.issueTime(Date.from(NOW.minusSeconds(600))).expirationTime(Date.from(NOW.minusSeconds(61)));
            case "subject" -> claims.subject(null);
            case "nonce" -> claims.claim("nonce", null);
            case "emptyNonce" -> claims.claim("nonce", "");
            case "blankNonce" -> claims.claim("nonce", " ");
            case "iat" -> claims.issueTime(null);
            case "exp" -> claims.expirationTime(null);
            case "numericSubject" -> claims.claim("sub", 12);
            case "numericNonce" -> claims.claim("nonce", 12);
            default -> throw new IllegalArgumentException();
        }
        // when / then
        rejected(adapter(), fake.sign(claims.build()), AuthErrorCode.INVALID_SOCIAL_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "HS256", "ES256", "otherRsa", "tampered"})
    @DisplayName("서명 변조와 허용하지 않은 알고리즘을 거부한다")
    void invalidSignatures(String kind) throws Exception {
        // given
        var claims = claims().build();
        String token;
        if (kind.equals("none")) token = new PlainJWT(claims).serialize();
        else if (kind.equals("otherRsa"))
            token = FakeOidcProvider.sign(
                    claims, new RSAKeyGenerator(2048).keyID("initial").generate());
        else if (kind.equals("tampered")) {
            var original = fake.sign(claims);
            int start = original.lastIndexOf('.') + 1;
            token = original.substring(0, start)
                    + (original.charAt(start) == 'A' ? 'B' : 'A')
                    + original.substring(start + 1);
        } else {
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.parse(kind)), claims);
            if (kind.equals("HS256")) jwt.sign(new MACSigner(new byte[32]));
            else jwt.sign(new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate()));
            token = jwt.serialize();
        }
        // when / then
        rejected(adapter(), token, AuthErrorCode.INVALID_SOCIAL_TOKEN);
    }

    @Test
    @DisplayName("만료 후 정확히 60초까지 허용한다")
    void expiryBoundary() throws Exception {
        // given
        var token = fake.sign(claims().issueTime(Date.from(NOW.minusSeconds(600)))
                .expirationTime(Date.from(NOW.minusSeconds(60)))
                .build());
        // when / then
        assertThat(adapter().verify(token).subject()).isEqualTo("subject");
    }

    @Test
    @DisplayName("구글은 스킴 없는 발급자도 허용한다")
    void googleIssuerWithoutScheme() throws Exception {
        // given
        var adapter = new GoogleLoginAdapter(properties(List.of("client")), clock, new MockEnvironment());
        // when / then
        assertThat(adapter.verify(
                                fake.sign(claims().issuer("accounts.google.com").build()))
                        .provider())
                .isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("새 키 ID가 나타나면 JWKS를 다시 조회한다")
    void refreshesOnNewKeyId() throws Exception {
        // given
        var adapter = adapter();
        adapter.verify(fake.sign(claims().build()));
        int before = fake.requests();
        fake.rotate("rotated");
        // when
        var identity = adapter.verify(fake.sign(claims().build()));
        // then
        assertThat(identity.subject()).isEqualTo("subject");
        assertThat(fake.requests()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("모르는 kid 100개는 모두 401이고 추가 JWKS 조회는 한 번 이하이다")
    void limitsUnknownKeyFlood() throws Exception {
        // given
        var adapter = adapter();
        adapter.verify(fake.sign(claims().build()));
        var unknown = new RSAKeyGenerator(2048).keyID("unknown").generate();
        int before = fake.requests();
        // when
        for (int i = 0; i < 100; i++) {
            var key = new com.nimbusds.jose.jwk.RSAKey.Builder(unknown)
                    .keyID("unknown-" + i)
                    .build();
            rejected(adapter, FakeOidcProvider.sign(claims().build(), key), AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }
        // then
        assertThat(fake.requests() - before).isBetween(0, 1);
    }

    @Test
    @DisplayName("Nimbus의 다음 허용 시각을 지난 뒤 모르는 kid를 다시 조회할 수 있다")
    void refreshesAfterRateLimitInterval() throws Exception {
        // given
        var adapter = adapter();
        adapter.verify(fake.sign(claims().build()));
        String unknown = FakeOidcProvider.sign(
                claims().build(), new RSAKeyGenerator(2048).keyID("unknown").generate());
        rejected(adapter, unknown, AuthErrorCode.INVALID_SOCIAL_TOKEN);
        rejected(adapter, unknown, AuthErrorCode.INVALID_SOCIAL_TOKEN);
        int before = fake.requests();
        // when: 대기 없이 Nimbus의 시간 경계만 이동한다.
        var decoder = org.springframework.test.util.ReflectionTestUtils.getField(adapter, "decoder");
        var processor = (com.nimbusds.jwt.proc.DefaultJWTProcessor<?>)
                org.springframework.test.util.ReflectionTestUtils.getField(decoder, "jwtProcessor");
        var selector = (com.nimbusds.jose.proc.JWSVerificationKeySelector<?>) processor.getJWSKeySelector();
        var source = ((com.nimbusds.jose.jwk.source.JWKSetBasedJWKSource<?>) selector.getJWKSource()).getJWKSetSource();
        while (!(source instanceof com.nimbusds.jose.jwk.source.RateLimitedJWKSetSource<?>)) {
            source = ((com.nimbusds.jose.jwk.source.JWKSetSourceWrapper<?>) source).getSource();
        }
        assertThat(((com.nimbusds.jose.jwk.source.RateLimitedJWKSetSource<?>) source).getMinTimeInterval())
                .isEqualTo(30_000);
        org.springframework.test.util.ReflectionTestUtils.setField(source, "nextOpeningTime", 0L);
        rejected(adapter, unknown, AuthErrorCode.INVALID_SOCIAL_TOKEN);
        // then
        assertThat(fake.requests()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("JWKS 500은 공급자 장애로 분류한다")
    void remoteServerFailure() throws Exception {
        // given
        fake.fail();
        var token = fake.sign(claims().build());
        // when / then
        rejected(adapter(), token, AuthErrorCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("JWKS 읽기 타임아웃은 공급자 장애로 분류한다")
    void remoteTimeout() throws Exception {
        // given
        fake.delay();
        var token = fake.sign(claims().build());
        // when / then
        rejected(adapter(), token, AuthErrorCode.PROVIDER_UNAVAILABLE);
    }

    @Test
    @DisplayName("허용 대상이 없으면 정상 서명도 거부한다")
    void emptyAudiencesFailClosed() throws Exception {
        // given
        var adapter = new KakaoLoginAdapter(properties(List.of()), clock, new MockEnvironment());
        // when / then
        rejected(adapter, fake.sign(claims().build()), AuthErrorCode.INVALID_SOCIAL_TOKEN);
    }

    @Test
    @DisplayName("이메일 검증 상태는 불리언 true와 문자열 true만 인정한다")
    void extractsEmailVerificationStrictly() throws Exception {
        // given
        var adapter = adapter();
        // when / then
        for (Object verified : new Object[] {true, "true", false, "false", "TRUE", 1}) {
            var identity = adapter.verify(fake.sign(claims().build(), "hong@gmail.com", verified));
            assertThat(identity.email()).isEqualTo("hong@gmail.com");
            assertThat(identity.emailVerified()).isEqualTo(Boolean.TRUE.equals(verified) || "true".equals(verified));
            assertThat(identity.toString()).doesNotContain("hong@gmail.com");
        }
    }

    @Test
    @DisplayName("이메일 타입과 길이가 잘못되면 이메일만 버리고 로그인은 허용한다")
    void ignoresInvalidEmail() throws Exception {
        // given
        var adapter = adapter();
        // when / then
        for (Object email : new Object[] {123, java.util.Map.of("x", "y"), "invalid", "a".repeat(315) + "@b.com"}) {
            var identity = adapter.verify(fake.sign(claims().build(), email, true));
            assertThat(identity.subject()).isEqualTo("subject");
            assertThat(identity.email()).isNull();
            assertThat(identity.emailVerified()).isFalse();
        }
        var boundary = adapter.verify(fake.sign(claims().build(), "a".repeat(314) + "@b.com", true));
        assertThat(boundary.email()).hasSize(320);
    }
}

class OidcSocialLoginFailureClassificationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("JWKS 재조회 제한에 걸린 모르는 kid 토큰은 401로 분류한다")
    void unknownKeyRateLimitIsInvalidToken(boolean wrapped) {
        // given: 중첩 여부와 무관하게 제한 예외를 401로 분류한다.
        var rateLimit = new RateLimitReachedException();
        var failure = new JwtException(
                "JWKS refresh rate limit",
                wrapped ? new KeySourceException("JWKS lookup failed", rateLimit) : rateLimit);
        // when / then
        assertThat(OidcSocialLoginAdapter.unavailable(failure)).isFalse();
    }
}
