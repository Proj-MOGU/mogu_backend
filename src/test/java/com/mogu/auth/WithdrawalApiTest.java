package com.mogu.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mogu.auth.api.WithdrawalRequest;
import com.mogu.auth.application.*;
import com.mogu.auth.domain.*;
import com.mogu.auth.support.FakeOidcProvider;
import com.mogu.auth.support.IdentityIntegrationSupport;
import com.mogu.auth.support.MutableAuthClock;
import com.mogu.common.event.MemberWithdrawn;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Import(WithdrawalApiTest.Withdrawals.class)
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
        properties = {
            "logging.level.com.mogu=DEBUG",
            "logging.level.org.springframework.web=DEBUG",
            "logging.level.org.springframework.security=DEBUG"
        })
class WithdrawalApiTest extends IdentityIntegrationSupport {
    @TestBean(name = "authClock", methodName = "mutableAuthClock", enforceOverride = true)
    private Clock testClock;

    static Clock mutableAuthClock() {
        return new MutableAuthClock();
    }

    @MockitoSpyBean
    private SocialTokenVerificationService socialVerification;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private Withdrawals withdrawals;

    @Autowired
    WithdrawalApiTest(
            MockMvc mvc, JdbcClient jdbc, NonceService nonces, @Qualifier("authClock") Clock clock, JsonMapper json) {
        super(mvc, jdbc, nonces, clock, json);
    }

    @BeforeEach
    void resetLimits() {
        ((MutableAuthClock) clock).reset();
        withdrawals.count.set(0);
        jdbc.sql("TRUNCATE auth_rate_limit_window, auth_rate_limit_bucket, auth_rate_limit_streak")
                .update();
    }

    @Test
    @DisplayName("새 nonce로 재인증해 탈퇴하면 갱신과 계정 조회가 401이고 재가입은 403이다")
    void withdrawsAndRejectsRefreshAccountAndRejoin() throws Exception {
        // given
        var registered = register("kakao", "withdraw-api", PHONE_A);
        String access = registered.get("accessToken").asText();
        String reauth = reauthToken("withdraw-api");
        doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isFalse();
                    return call.callRealMethod();
                })
                .when(socialVerification)
                .verifyToken(AuthProvider.KAKAO, reauth);
        // when / then
        withdraw(access, "kakao", reauth)
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        mvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(json.writeValueAsString(new com.mogu.auth.api.RefreshRequest(
                                registered.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me/account").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        String pending = pending("kakao", "withdraw-api");
        complete(pending, verification(pending, PHONE_A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_REJOIN_RESTRICTED"));
    }

    @Test
    @DisplayName("다른 회원의 소셜 계정과 연결되지 않은 공급자는 재인증 실패 403이며 계정을 유지한다")
    void rejectsOtherIdentityAndUnlinkedProvider() throws Exception {
        // given
        account("mine", AccountStatus.ACTIVE, null);
        account("other", AccountStatus.ACTIVE, null);
        String access = successfulLogin("mine").get("accessToken").asText();
        // when / then
        forbidden(access, "kakao", token("other"));
        forbidden(access, "google", token("google", "mine"));
        assertThat(jdbc.sql("SELECT count(*) FROM auth_account WHERE status = 'ACTIVE'")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
        assertThat(count("auth_social_identity")).isEqualTo(2);
        assertThat(events.stream(MemberWithdrawn.class)).isEmpty();
    }

    @Test
    @DisplayName("잘못된 서명과 이미 쓴 nonce는 401이 아닌 재인증 실패 403이다")
    void rejectsBadSignatureAndConsumedNonce() throws Exception {
        // given
        account("signature", AccountStatus.ACTIVE, null);
        String consumed = token("signature");
        String access = json.readTree(login("kakao", consumed)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("accessToken")
                .asText();
        var claims = SignedJWT.parse(token("signature")).getJWTClaimsSet();
        try (var rogue = new FakeOidcProvider()) {
            // when / then: same kid, different signing key
            forbidden(access, "kakao", rogue.sign(claims));
        }
        forbidden(access, "kakao", consumed);
        assertThat(jdbc.sql("SELECT status FROM auth_account")
                        .query(String.class)
                        .single())
                .isEqualTo("ACTIVE");
        assertThat(events.stream(MemberWithdrawn.class)).isEmpty();
    }

    @Test
    @DisplayName("이미 탈퇴하면 남은 Access Token으로 재요청해도 검증 호출과 추가 이벤트 없이 204다")
    void alreadyWithdrawnSkipsVerification() throws Exception {
        // given
        account("idempotent", AccountStatus.ACTIVE, null);
        String access = successfulLogin("idempotent").get("accessToken").asText();
        withdraw(access, "kakao", reauthToken("idempotent")).andExpect(status().isNoContent());
        long published = events.stream(MemberWithdrawn.class).count();
        clearInvocations(socialVerification);
        int requests = fake.requests();
        // when / then
        withdraw(access, "kakao", "invalid")
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(socialVerification);
        assertThat(fake.requests()).isEqualTo(requests);
        assertThat(events.stream(MemberWithdrawn.class).count())
                .isEqualTo(published)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Access Token 없는 요청은 401이고 미지원 공급자와 빈 ID Token은 400이다")
    void rejectsAuthenticationAndInputErrors() throws Exception {
        // given
        account("input", AccountStatus.ACTIVE, null);
        String access = successfulLogin("input").get("accessToken").asText();
        // when / then
        mvc.perform(post("/api/auth/withdrawal")
                        .contentType("application/json")
                        .content(json.writeValueAsString(new WithdrawalRequest("kakao", "token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        assertThat(jdbc.sql("SELECT count(*) FROM auth_rate_limit_window WHERE rule_name = 'withdrawal-requests'")
                        .query(Long.class)
                        .single())
                .isZero();
        withdraw(access, "apple", "token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_UNSUPPORTED_PROVIDER"));
        withdraw(access, "kakao", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    @DisplayName("동일 IP라도 회원별 5회만 허용하고 다음 분에는 회복한다")
    void limitsByMemberAndRecovers() throws Exception {
        // given
        var firstId = account("limited", AccountStatus.ACTIVE, null);
        account("separate", AccountStatus.ACTIVE, null);
        String first = successfulLogin("limited").get("accessToken").asText();
        String second = successfulLogin("separate").get("accessToken").asText();
        for (int i = 0; i < 5; i++) forbidden(first, "kakao", "invalid");
        // when / then
        var response = withdraw(first, "kakao", token("limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_TOO_MANY_REQUESTS"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse();
        assertThat(response.getHeader("Retry-After")).matches("[1-9][0-9]*");
        withdraw(second, "kakao", token("separate")).andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT request_count FROM auth_rate_limit_window WHERE rule_name ="
                                + " 'withdrawal-requests' AND limit_key = :key")
                        .param("key", firstId.value().toString())
                        .query(Integer.class)
                        .single())
                .isEqualTo(5);
        ((MutableAuthClock) clock).advance(Duration.ofMinutes(1));
        withdraw(first, "kakao", token("limited")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DEBUG 로그에도 탈퇴 ID Token과 subject 및 이메일이 노출되지 않는다")
    void redactsWithdrawalSecrets(CapturedOutput output) throws Exception {
        // given
        String subject = "withdrawal-private-subject";
        String email = "withdrawal-private@example.com";
        account(subject, AccountStatus.ACTIVE, null);
        String access = successfulLogin(subject).get("accessToken").asText();
        String valid = token("kakao", subject, email, true);
        String invalid = "private-invalid-id-token";
        org.slf4j.LoggerFactory.getLogger(getClass()).debug("withdrawal-redaction-marker");
        // when
        forbidden(access, "kakao", invalid);
        withdraw(access, "kakao", valid).andExpect(status().isNoContent());
        // then
        assertThat(output.getAll())
                .contains("withdrawal-redaction-marker")
                .doesNotContain(valid, invalid, subject, email);
        assertThat(new WithdrawalRequest("kakao", valid).toString()).doesNotContain(valid);
    }

    @Test
    @DisplayName("같은 nonce의 동시 탈퇴 요청 두 건은 모두 204이고 이벤트는 한 번 발행한다")
    void concurrentWithdrawalWithSameNonceIsIdempotent() throws Exception {
        // given
        account("same-nonce", AccountStatus.ACTIVE, null);
        String access = successfulLogin("same-nonce").get("accessToken").asText();
        String reauth = reauthToken("same-nonce");
        var verified = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        doAnswer(call -> {
                    var identity = call.callRealMethod();
                    verified.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    return identity;
                })
                .when(socialVerification)
                .verifyToken(AuthProvider.KAKAO, reauth);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MockHttpServletResponse> request =
                    () -> withdraw(access, "kakao", reauth).andReturn().getResponse();
            var first = executor.submit(request);
            var second = executor.submit(request);
            // when
            try {
                assertThat(verified.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            // then
            assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactly(204, 204);
        }
        assertThat(withdrawals.count.get()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM auth_account")
                        .query(String.class)
                        .single())
                .isEqualTo("WITHDRAWN");
        assertThat(count("auth_social_identity")).isZero();
    }

    @Test
    @DisplayName("만료된 nonce로 탈퇴하면 유효한 ID Token이어도 재인증 실패 403이다")
    void rejectsExpiredNonce() throws Exception {
        // given
        account("expired-nonce", AccountStatus.ACTIVE, null);
        String access = successfulLogin("expired-nonce").get("accessToken").asText();
        var expired = nonces.issue();
        ((MutableAuthClock) clock)
                .advance(Duration.between(clock.instant(), expired.expiresAt()).plusNanos(1000));
        String validTokenWithExpiredNonce = fake.sign(
                new JWTClaimsSet.Builder(SignedJWT.parse(token("expired-nonce")).getJWTClaimsSet())
                        .claim("nonce", expired.value())
                        .build());
        long usedNonces = count("auth_used_nonce");
        // when / then
        forbidden(access, "kakao", validTokenWithExpiredNonce);
        assertThat(jdbc.sql("SELECT status FROM auth_account")
                        .query(String.class)
                        .single())
                .isEqualTo("ACTIVE");
        assertThat(count("auth_social_identity")).isEqualTo(1);
        assertThat(count("auth_used_nonce")).isEqualTo(usedNonces);
        assertThat(events.stream(MemberWithdrawn.class)).isEmpty();
    }

    public static class Withdrawals {
        final AtomicInteger count = new AtomicInteger();

        @EventListener
        public void withdrawn(MemberWithdrawn ignored) {
            count.incrementAndGet();
        }
    }

    private String reauthToken(String subject) throws Exception {
        String nonce = json.readTree(mvc.perform(post("/api/auth/nonce"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("nonce")
                .asText();
        return fake.sign(
                new JWTClaimsSet.Builder(SignedJWT.parse(token(subject)).getJWTClaimsSet())
                        .claim("nonce", nonce)
                        .build());
    }

    private ResultActions withdraw(String access, String provider, String token) throws Exception {
        return mvc.perform(post("/api/auth/withdrawal")
                .header("Authorization", "Bearer " + access)
                .contentType("application/json")
                .content(json.writeValueAsString(new WithdrawalRequest(provider, token))));
    }

    private void forbidden(String access, String provider, String token) throws Exception {
        withdraw(access, provider, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_REAUTHENTICATION_FAILED"));
    }
}
