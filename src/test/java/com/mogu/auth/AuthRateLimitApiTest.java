package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.infrastructure.AuthRateLimitFilter;
import com.mogu.auth.support.IdentityIntegrationSupport;
import com.mogu.auth.support.MutableAuthClock;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "mogu.auth.rate-limit.rules.nonce-requests.limit=2",
            "mogu.auth.rate-limit.rules.login-requests.limit=10",
            "mogu.auth.rate-limit.rules.refresh-requests.limit=4",
            "mogu.auth.rate-limit.rules.logout-requests.limit=4",
            "mogu.auth.rate-limit.rules.login-failures.limit=2",
            "mogu.auth.rate-limit.rules.refresh-failures.limit=2",
            "mogu.auth.rate-limit.rules.identity-failures.limit=2",
            "mogu.auth.rate-limit.rules.identity-requests.limit=4"
        })
class AuthRateLimitApiTest extends IdentityIntegrationSupport {
    @TestBean(name = "authClock", methodName = "mutableClock", enforceOverride = true)
    private Clock testClock;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.mogu.auth.application.LoginService loginService;

    static Clock mutableClock() {
        return new MutableAuthClock();
    }

    private final FilterChainProxy filterChainProxy;

    @Autowired
    AuthRateLimitApiTest(
            MockMvc mvc,
            JdbcClient jdbc,
            NonceService nonces,
            @Qualifier("authClock") Clock clock,
            JsonMapper json,
            FilterChainProxy filterChainProxy) {
        super(mvc, jdbc, nonces, clock, json);
        this.filterChainProxy = filterChainProxy;
    }

    @BeforeEach
    void resetLimits() {
        assertThat(clock).isSameAs(testClock);
        ((MutableAuthClock) clock).reset();
        jdbc.sql("TRUNCATE auth_rate_limit_window, auth_rate_limit_bucket, auth_rate_limit_streak")
                .update();
    }

    @Test
    @DisplayName("전체 컨텍스트에서 두 보안 체인에 횟수 제한 필터를 올바른 순서로 등록한다")
    void registersRateLimitFilterInBothChains() {
        // given
        var chains = filterChainProxy.getFilterChains();
        assertThat(chains).hasSize(2);
        // when
        var registrationFilters = chains.get(0).getFilters();
        var generalFilters = chains.get(1).getFilters();
        // then
        for (var filters : java.util.List.of(registrationFilters, generalFilters)) {
            assertThat(filters)
                    .filteredOn(AuthRateLimitFilter.class::isInstance)
                    .hasSize(1);
            assertThat(filters)
                    .filteredOn(BearerTokenAuthenticationFilter.class::isInstance)
                    .hasSize(1);
        }
        var registrationTypes =
                registrationFilters.stream().map(Object::getClass).toList();
        var generalTypes = generalFilters.stream().map(Object::getClass).toList();
        assertThat(registrationTypes.indexOf(AuthRateLimitFilter.class))
                .isGreaterThan(registrationTypes.indexOf(BearerTokenAuthenticationFilter.class));
        assertThat(generalTypes.indexOf(AuthRateLimitFilter.class))
                .isLessThan(generalTypes.indexOf(BearerTokenAuthenticationFilter.class));
    }

    @Test
    @DisplayName("nonce 한도와 응답 계약을 지키고 차단 뒤 쓰기 없이 다음 구간에서 회복한다")
    void nonceWindowAndResponseContract() throws Exception {
        // given
        nonce("10.0.0.1", "1").andExpect(status().isOk());
        nonce("10.0.0.1", "2").andExpect(status().isOk());
        // when / then
        for (int i = 0; i < 10; i++) {
            var response = nonce("10.0.0.1", "spoof-" + i)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().exists("X-Trace-Id"))
                    .andExpect(jsonPath("$.code").value("AUTH_TOO_MANY_REQUESTS"))
                    .andReturn()
                    .getResponse();
            assertThat(response.getHeader("Retry-After")).matches("[1-9][0-9]*");
            assertThat(response.getContentAsString()).doesNotContain("10.0.0.1", "spoof");
        }
        assertThat(jdbc.sql("SELECT request_count FROM auth_rate_limit_window WHERE rule_name =" + " 'nonce-requests'")
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
        nonce("10.0.0.2", "1").andExpect(status().isOk());
        refresh().andExpect(status().isUnauthorized());
        ((MutableAuthClock) clock).advance(Duration.ofMinutes(1));
        nonce("10.0.0.1", "1").andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 실패 한도에 도달하면 정상 토큰도 차단하고 15분마다 한 번 회복한다")
    void blocksValidLoginUntilBucketRefills() throws Exception {
        // given
        login("kakao", "invalid").andExpect(status().isUnauthorized());
        login("kakao", "invalid").andExpect(status().isUnauthorized());
        String valid = token("valid");
        // when / then
        login("kakao", valid).andExpect(status().isTooManyRequests());
        ((MutableAuthClock) clock).advance(Duration.ofMinutes(15));
        login("kakao", token("after-refill")).andExpect(status().isOk());
        login("kakao", "invalid").andExpect(status().isUnauthorized());
        login("kakao", token("blocked-again")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("입력 오류와 공급자 장애와 제재 응답은 로그인 실패로 기록하지 않는다")
    void excludesInputOutageAndSanction() throws Exception {
        // given / when
        login("unsupported", "token").andExpect(status().isBadRequest());
        login("kakao", "").andExpect(status().isBadRequest());
        account("sanctioned", com.mogu.auth.domain.AccountStatus.SANCTIONED, null);
        login("kakao", token("sanctioned")).andExpect(status().isForbidden());
        org.mockito.Mockito.doThrow(new com.mogu.common.error.BusinessException(
                        com.mogu.auth.domain.AuthErrorCode.PROVIDER_UNAVAILABLE))
                .when(loginService)
                .login(com.mogu.auth.domain.AuthProvider.KAKAO, "provider-outage");
        login("kakao", "provider-outage").andExpect(status().isServiceUnavailable());
        // then
        assertThat(count("auth_rate_limit_bucket")).isZero();
    }

    @Test
    @DisplayName("없는 리프레시 토큰의 실패 한도 이후 요청을 차단한다")
    void refreshFailuresBlock() throws Exception {
        // given
        refresh().andExpect(status().isUnauthorized());
        refresh().andExpect(status().isUnauthorized());
        // when / then
        refresh().andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("본인인증 연속 실패는 가입 시도별로 차단하고 성공하면 초기화한다")
    void identityFailureStreakAndSuccessReset() throws Exception {
        // given
        String first = pending("kakao", "first");
        String second = pending("kakao", "second");
        complete(first, "missing").andExpect(status().isBadRequest());
        complete(first, "missing").andExpect(status().isBadRequest());
        // when / then
        complete(first, "missing").andExpect(status().isTooManyRequests());
        complete(second, "missing").andExpect(status().isBadRequest());
        complete(second, verification(second, "01012345678")).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT consecutive_failures FROM auth_rate_limit_streak")
                        .query(Integer.class)
                        .list())
                .containsExactly(2);
        ((MutableAuthClock) clock).advance(Duration.ofSeconds(1));
        complete(second, "missing-again").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("동일 IP라도 가입 시도별 요청 수는 분리하고 위조 토큰은 세지 않는다")
    void separatesRegistrationRequestsAndRejectsUnauthenticated() throws Exception {
        // given
        String first = pending("kakao", "first");
        String second = pending("kakao", "second");
        for (int i = 0; i < 4; i++) mock(first, "01012345678", null).andExpect(status().isOk());
        // when / then
        mock(first, "01012345678", null).andExpect(status().isTooManyRequests());
        mock(second, "01012345678", null).andExpect(status().isOk());
        complete("forged", "missing").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/identity-verification")).andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("SELECT count(*) FROM auth_rate_limit_window WHERE rule_name='identity-requests'")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("로그인 이후 일반 API는 인증 요청 수 제한을 적용하지 않는다")
    void normalApiIsNotLimited() throws Exception {
        // given
        String access =
                register("kakao", "member", "01012345678").get("accessToken").asText();
        // when / then
        for (int i = 0; i < 100; i++)
            mvc.perform(get("/test/login-member").header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT count(*) FROM auth_rate_limit_window WHERE rule_name='nonce-requests'")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    @DisplayName("로그인과 갱신과 로그아웃 요청 수를 각각 제한한다")
    void limitsOtherPublicEndpoints() throws Exception {
        // given / when / then
        for (int i = 0; i < 10; i++) login("kakao", "").andExpect(status().isBadRequest());
        login("kakao", "").andExpect(status().isTooManyRequests());
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/refresh")
                            .contentType("application/json")
                            .content("{\"refreshToken\":\"\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        }
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isTooManyRequests());
        assertThat(count("auth_rate_limit_bucket")).isZero();
    }

    private ResultActions nonce(String ip, String forwarded) throws Exception {
        return mvc.perform(post("/api/auth/nonce")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .header("X-Forwarded-For", forwarded)
                .header("X-Real-IP", forwarded)
                .header("Forwarded", "for=" + forwarded));
    }

    private ResultActions refresh() throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .with(request -> {
                    request.setRemoteAddr("10.0.0.1");
                    return request;
                })
                .contentType("application/json")
                .content("{\"refreshToken\":\"" + "a".repeat(43) + "\"}"));
    }
}
