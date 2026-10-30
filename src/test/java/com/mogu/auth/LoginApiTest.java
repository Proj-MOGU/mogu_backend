package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.support.LoginIntegrationSupport;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class LoginApiTest extends LoginIntegrationSupport {
    private final HikariDataSource dataSource;

    @Autowired
    LoginApiTest(
            MockMvc mvc,
            JdbcClient jdbc,
            NonceService nonces,
            @Qualifier("authClock") Clock clock,
            JsonMapper json,
            DataSource dataSource) {
        super(mvc, jdbc, nonces, clock, json);
        this.dataSource = (HikariDataSource) dataSource;
    }

    @BeforeEach
    void clearRecords() {
        jdbc.sql("TRUNCATE auth_refresh_token, auth_social_identity, auth_account, auth_used_nonce")
                .update();
    }

    @Test
    @DisplayName("정상 로그인 토큰을 발급하고 회원 식별자를 주입하며 해시만 저장한다")
    void loginStoresHashAndResolvesMember() throws Exception {
        // given
        var member = account("active", AccountStatus.ACTIVE, null);
        // when
        var response = login("kakao", token("active"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse();
        var body = json.readTree(response.getContentAsString());
        // then
        assertThat(body.get("registrationRequired").asBoolean()).isFalse();
        assertThat(body.has("registrationToken")).isFalse();
        mvc.perform(get("/test/login-member")
                        .header(
                                "Authorization",
                                "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(content().string(member.toString()));
    }

    @Test
    @DisplayName("Refresh Token은 해시만 저장하고 발급 시각부터 30일 후 만료된다")
    void refreshTokenStoresOnlyHashWithExpiry() throws Exception {
        // given
        account("hash", AccountStatus.ACTIVE, null);
        // when
        var body = successfulLogin("hash");
        // then
        String raw = body.get("refreshToken").asText();
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        String hash = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.sql("SELECT token_hash FROM auth_refresh_token")
                        .query(String.class)
                        .list())
                .containsExactly(hash);
        var times = jdbc.sql("SELECT issued_at,expires_at FROM auth_refresh_token")
                .query((rs, n) -> List.of(
                        rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()))
                .single();
        assertThat(times.get(1))
                .isEqualTo(times.getFirst().plus(Duration.ofDays(30)))
                .isEqualTo(Instant.parse(body.get("refreshTokenExpiresAt").asText()));
        for (String table : List.of("auth_account", "auth_social_identity", "auth_refresh_token", "auth_used_nonce"))
            assertThat(jdbc.sql("SELECT row_to_json(t)::text FROM " + table + " t")
                            .query(String.class)
                            .list())
                    .allSatisfy(row -> assertThat(row).doesNotContain(raw));
    }

    @Test
    @DisplayName("같은 계정의 로그인마다 새로운 Refresh Token 패밀리를 발급한다")
    void eachLoginCreatesFamily() throws Exception {
        // given
        account("repeat", AccountStatus.ACTIVE, null);
        // when
        successfulLogin("repeat");
        successfulLogin("repeat");
        // then
        assertThat(jdbc.sql("SELECT family_id FROM auth_refresh_token")
                        .query(UUID.class)
                        .list())
                .hasSize(2)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("연결 계정이 없으면 회원 생성 없이 가입 대기 토큰만 발급한다")
    void unlinkedReturnsRegistration() throws Exception {
        // given
        var before =
                jdbc.sql("SELECT count(*) FROM auth_account").query(Long.class).single();
        // when
        var body = successfulLogin("new");
        // then
        assertThat(body.get("registrationRequired").asBoolean()).isTrue();
        assertThat(body.get("registrationToken").asText()).isNotBlank();
        assertThat(body.has("registrationTokenExpiresAt")).isTrue();
        assertThat(body.has("accessToken")).isFalse();
        assertThat(body.has("refreshToken")).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM auth_account")
                        .query(Long.class)
                        .single())
                .isEqualTo(before);
        assertThat(jdbc.sql("SELECT count(*) FROM auth_refresh_token")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("제재 계정도 nonce 소모를 커밋하고 재요청을 거부한다")
    void sanctionedConsumesNonce() throws Exception {
        // given
        account("sanctioned", AccountStatus.SANCTIONED, clock.instant().plusSeconds(300));
        var token = token("sanctioned");
        // when / then
        login("kakao", token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_SANCTIONED"));
        login("kakao", token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_SOCIAL_TOKEN"));
        assertThat(jdbc.sql("SELECT count(*) FROM auth_used_nonce")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM auth_refresh_token")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("제재 기한이 지나면 정상 로그인한다")
    void expiredSanctionAllowsLogin() throws Exception {
        // given
        account("expired", AccountStatus.SANCTIONED, clock.instant().minusSeconds(1));
        // when / then
        assertThat(successfulLogin("expired").get("registrationRequired").asBoolean())
                .isFalse();
    }

    @Test
    @DisplayName("미지원 공급자는 400으로 거부한다")
    void rejectsUnsupportedProvider() throws Exception {
        // given / when / then
        login("apple", "token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_UNSUPPORTED_PROVIDER"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 8193})
    @DisplayName("비어 있거나 공백이거나 너무 긴 ID Token을 거부한다")
    void rejectsInvalidInput(int length) throws Exception {
        // given
        String value = length == 1 ? " " : "a".repeat(length);
        // when / then
        login("kakao", value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    @DisplayName("잘못된 ID Token은 401로 거부한다")
    void rejectsInvalidToken() throws Exception {
        // given / when / then
        login("kakao", "invalid")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_SOCIAL_TOKEN"));
    }

    @Test
    @DisplayName("로그인과 가입 대기 응답 모두 캐시 저장을 금지한다")
    void responsesDisableCaching() throws Exception {
        // given
        account("cache-active", AccountStatus.ACTIVE, null);
        // when / then
        for (String subject : List.of("cache-active", "cache-pending"))
            login("kakao", token(subject))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("서로 다른 nonce로 같은 계정에 동시에 로그인하면 다섯 패밀리를 발급한다")
    void concurrentLoginsCreateIndependentFamilies() throws Exception {
        // given
        account("concurrent", AccountStatus.ACTIVE, null);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 5; i++) tokens.add(token("concurrent"));
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(5);
        List<Future<Integer>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(5)) {
            for (var token : tokens)
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    return login("kakao", token).andReturn().getResponse().getStatus();
                }));
            // when
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            // then
            for (var result : results)
                assertThat(result.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(jdbc.sql("SELECT family_id FROM auth_refresh_token")
                            .query(UUID.class)
                            .list())
                    .hasSize(5)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("JWKS 응답을 기다리는 동안 DB 커넥션을 점유하지 않는다")
    void delayedJwksDoesNotHoldConnection() throws Exception {
        // given
        fake.rotate("delayed-" + UUID.randomUUID());
        fake.delay();
        var token = token("delayed");
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(
                    () -> login("kakao", token).andReturn().getResponse().getStatus());
            try {
                // when
                assertThat(fake.awaitDelayedRequest()).isTrue();
                // then
                assertThat(dataSource.getHikariPoolMXBean().getActiveConnections())
                        .isZero();
            } finally {
                fake.resume();
            }
            assertThat(result.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
    }
}
