package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mogu.auth.api.PendingSocialIdentity;
import com.mogu.auth.application.RateLimitRule;
import com.mogu.auth.application.RateLimiter;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.common.error.GlobalExceptionHandler;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class AuthRateLimitFilterTest {
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(limiter, new JsonMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
        "login/kakao,401,AUTH_INVALID_SOCIAL_TOKEN,LOGIN_FAILURES,true",
        "login/kakao,503,AUTH_PROVIDER_UNAVAILABLE,LOGIN_FAILURES,false",
        "login/kakao,400,COMMON_INVALID_INPUT,LOGIN_FAILURES,false",
        "login/kakao,403,AUTH_ACCOUNT_SANCTIONED,LOGIN_FAILURES,false",
        "login/kakao,401,AUTH_UNAUTHORIZED,LOGIN_FAILURES,false",
        "login/kakao,400,AUTH_INVALID_SOCIAL_TOKEN,LOGIN_FAILURES,false",
        "refresh,401,AUTH_INVALID_REFRESH_TOKEN,REFRESH_FAILURES,true",
        "refresh,400,COMMON_INVALID_INPUT,REFRESH_FAILURES,false",
        "identity-verification,400,AUTH_INVALID_IDENTITY_VERIFICATION,IDENTITY_FAILURES,true",
        "identity-verification,400,COMMON_INVALID_INPUT,IDENTITY_FAILURES,false",
        "mock-identity-verifications,400,AUTH_INVALID_IDENTITY_VERIFICATION,IDENTITY_FAILURES,false"
    })
    @DisplayName("응답 상태와 오류 코드가 정확히 일치하는 실패만 응답 이후 기록한다")
    void recordsOnlyDefinedFailures(String path, int status, String code, RateLimitRule failure, boolean counted)
            throws Exception {
        // given
        var request = new MockHttpServletRequest("POST", "/api/auth/" + path);
        request.setRemoteAddr("::1");
        String key = path.contains("identity") ? authenticateRegistration() : "::1";
        var response = new MockHttpServletResponse();
        // when
        filter.doFilter(request, response, (req, res) -> {
            verify(limiter, never()).recordFailure(any(), anyString());
            request.setAttribute(GlobalExceptionHandler.ERROR_CODE_ATTRIBUTE, code);
            response.setStatus(status);
        });
        // then
        if (counted) verify(limiter).recordFailure(failure, key);
        else verify(limiter, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("이미 소진된 실패 버킷은 다음 처리와 요청 카운터 쓰기 전에 차단한다")
    void rejectsBeforeDownstreamOrWrites() throws Exception {
        // given
        var request = new MockHttpServletRequest("POST", "/api/auth/login/google");
        request.setRemoteAddr("::1");
        request.addHeader("X-Forwarded-For", "different");
        when(limiter.blockedFor(RateLimitRule.LOGIN_FAILURES, "::1")).thenReturn(900L);
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        // when
        filter.doFilter(request, response, (req, res) -> called.set(true));
        // then
        assertThat(called).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("900");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsString())
                .contains("AUTH_TOO_MANY_REQUESTS")
                .doesNotContain("::1", "different");
        verify(limiter, never()).tryConsume(any(), anyString());
        verify(limiter, never()).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("본인인증 성공은 연속 실패를 초기화한다")
    void successResetsStreak() throws Exception {
        // given
        String key = authenticateRegistration();
        // when
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/auth/identity-verification"),
                new MockHttpServletResponse(),
                (req, res) -> {});
        // then
        verify(limiter).recordSuccess(RateLimitRule.IDENTITY_FAILURES, key);
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/auth/nonce", "POST,/test/private", "POST,/api/auth/identity-verification"})
    @DisplayName("대상이 아닌 요청과 인증되지 않은 가입 요청은 카운터를 사용하지 않는다")
    void ignoresUnmatchedRequests(String method, String path) throws Exception {
        // given / when
        filter.doFilter(new MockHttpServletRequest(method, path), new MockHttpServletResponse(), (req, res) -> {});
        // then
        verify(limiter, never()).tryConsume(any(), anyString());
    }

    private String authenticateRegistration() {
        var id = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new PendingRegistrationAuthentication(
                        new PendingSocialIdentity(id, AuthProvider.KAKAO, "subject", null, false)));
        return id.toString();
    }
}
