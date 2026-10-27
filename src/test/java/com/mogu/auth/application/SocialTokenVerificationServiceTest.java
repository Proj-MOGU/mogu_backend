package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.UsedNonceRepository;
import com.mogu.auth.support.FakeOidcProvider;
import com.mogu.common.error.BusinessException;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SocialTokenVerificationServiceTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    static final FakeOidcProvider fake = new FakeOidcProvider();

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "mogu.auth.social.kakao.jwk-set-uri", () -> fake.jwkSetUri().toString());
        registry.add("mogu.auth.social.kakao.issuers", () -> "https://kauth.kakao.com");
        registry.add("mogu.auth.social.kakao.audiences", () -> "test-client");
    }

    @AfterAll
    static void stopProvider() {
        fake.close();
    }

    private final SocialTokenVerificationService service;
    private final NonceService nonces;
    private final UsedNonceRepository repository;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Autowired
    SocialTokenVerificationServiceTest(
            SocialTokenVerificationService service,
            NonceService nonces,
            UsedNonceRepository repository,
            JdbcClient jdbc,
            @Qualifier("authClock") Clock clock) {
        this.service = service;
        this.nonces = nonces;
        this.repository = repository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @BeforeEach
    void clearRecords() {
        jdbc.sql("DELETE FROM auth_used_nonce").update();
    }

    private JWTClaimsSet claims(String nonce, String jti) {
        var now = clock.instant();
        return new JWTClaimsSet.Builder()
                .issuer("https://kauth.kakao.com")
                .audience("test-client")
                .subject("kakao-subject")
                .claim("nonce", nonce)
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
    }

    private void rejected(String token) {
        assertThatThrownBy(() -> service.verify(AuthProvider.KAKAO, token))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.INVALID_SOCIAL_TOKEN));
    }

    private long count() {
        return jdbc.sql("SELECT count(*) FROM auth_used_nonce")
                .query(Long.class)
                .single();
    }

    @Test
    @DisplayName("검증한 nonce는 원문 없이 SHA-256 해시로 한 행 저장한다")
    void storesOnlyHash() throws Exception {
        // given
        var nonce = nonces.issue();
        var token = fake.sign(claims(nonce.value(), "one"));
        // when
        var identity = service.verify(AuthProvider.KAKAO, token);
        // then
        assertThat(identity.subject()).isEqualTo("kakao-subject");
        String expected = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(nonce.value().getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.sql("SELECT nonce_hash FROM auth_used_nonce")
                        .query(String.class)
                        .list())
                .containsExactly(expected);
        assertThat(expected).matches("[0-9a-f]{64}");
        assertThat(jdbc.sql("SELECT column_name FROM information_schema.columns WHERE table_name = 'auth_used_nonce'")
                        .query(String.class)
                        .list())
                .containsExactlyInAnyOrder("nonce_hash", "expires_at", "used_at");
        assertThat(jdbc.sql("SELECT row_to_json(n)::text FROM auth_used_nonce n")
                        .query(String.class)
                        .single())
                .doesNotContain(nonce.value());
    }

    @Test
    @DisplayName("같은 토큰을 재사용하면 거부한다")
    void rejectsReplay() throws Exception {
        // given
        var token = fake.sign(claims(nonces.issue().value(), "one"));
        service.verify(AuthProvider.KAKAO, token);
        // when / then
        rejected(token);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("잘못된 서명은 nonce를 소모하지 않는다")
    void invalidSignatureDoesNotConsume() throws Exception {
        // given
        var claims = claims(nonces.issue().value(), "one");
        var forged = FakeOidcProvider.sign(
                claims, new RSAKeyGenerator(2048).keyID("initial").generate());
        // when / then
        rejected(forged);
        assertThat(count()).isZero();
        assertThat(service.verify(AuthProvider.KAKAO, fake.sign(claims)).subject())
                .isEqualTo("kakao-subject");
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("서버가 발급하지 않은 위조 nonce를 거부한다")
    void rejectsForgedNonce() throws Exception {
        // given
        var parts = nonces.issue().value().split("\\.");
        parts[3] = (parts[3].charAt(0) == 'A' ? "B" : "A") + parts[3].substring(1);
        var token = fake.sign(claims(String.join(".", parts), "one"));
        // when / then
        rejected(token);
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("같은 nonce의 두 토큰을 열 번 동시에 검증해도 한 번만 성공한다")
    void consumesExactlyOnceConcurrently() throws Exception {
        // given
        String nonce = nonces.issue().value();
        var tokens = List.of(fake.sign(claims(nonce, "first")), fake.sign(claims(nonce, "second")));
        var ready = new CountDownLatch(10);
        var start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                String token = tokens.get(i % 2);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    try {
                        service.verify(AuthProvider.KAKAO, token);
                        return true;
                    } catch (BusinessException e) {
                        assertThat(e.errorCode()).isEqualTo(AuthErrorCode.INVALID_SOCIAL_TOKEN);
                        return false;
                    }
                }));
            }
            // when
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            int successes = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS)) successes++;
            // then
            assertThat(successes).isEqualTo(1);
            assertThat(count()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("임계 시각 이전에 만료된 기록만 삭제한다")
    void deletesOnlyExpiredRows() {
        // given
        Instant threshold = clock.instant();
        repository.markUsed("a".repeat(64), threshold.minusSeconds(1));
        repository.markUsed("b".repeat(64), threshold);
        repository.markUsed("c".repeat(64), threshold.plusSeconds(1));
        // when
        int deleted = repository.deleteExpiredBefore(threshold);
        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(count()).isEqualTo(2);
    }
}
