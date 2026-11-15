package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mogu.auth.application.RateLimitRule;
import com.mogu.auth.application.RateLimiter;
import com.mogu.common.auth.MemberId;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class WithdrawalRateLimitFilterTest {
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final JsonMapper mapper = new JsonMapper();
    private final WithdrawalRateLimitFilter filter = new WithdrawalRateLimitFilter(limiter, mapper);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 전 IP 필터는 탈퇴를 세지 않으며 인증 뒤 회원 키로 한 번만 센다")
    void countsAfterAuthenticationOnly() throws Exception {
        // given
        var id = new MemberId(UUID.randomUUID());
        var calls = new AtomicInteger();
        var request = new MockHttpServletRequest("POST", "/api/auth/withdrawal");
        request.setRemoteAddr("same-ip");
        // when
        new AuthRateLimitFilter(limiter, mapper).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            verifyNoInteractions(limiter);
            SecurityContextHolder.getContext().setAuthentication(new MemberAuthentication(id));
            filter.doFilter(req, res, (ignoredRequest, ignoredResponse) -> calls.incrementAndGet());
        });
        // then
        verify(limiter).tryConsume(RateLimitRule.WITHDRAWAL_REQUESTS, id.value().toString());
        verifyNoMoreInteractions(limiter);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("인증되지 않은 요청과 다른 메서드 및 경로는 세지 않는다")
    void ignoresUnauthenticatedAndOtherPaths() throws Exception {
        // given
        var calls = new AtomicInteger();
        // when
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/auth/withdrawal"),
                new MockHttpServletResponse(),
                (req, res) -> calls.incrementAndGet());
        SecurityContextHolder.getContext().setAuthentication(new MemberAuthentication(new MemberId(UUID.randomUUID())));
        for (String[] path : new String[][] {{"GET", "/api/auth/withdrawal"}, {"POST", "/api/auth/logout"}})
            filter.doFilter(
                    new MockHttpServletRequest(path[0], path[1]),
                    new MockHttpServletResponse(),
                    (req, res) -> calls.incrementAndGet());
        // then
        verifyNoInteractions(limiter);
        assertThat(calls).hasValue(3);
    }

    @Test
    @DisplayName("6번째 요청은 429와 Retry-After를 반환하고 같은 IP의 다른 회원은 통과한다")
    void isolatesMembersAndRejectsBeforeController() throws Exception {
        // given: 실제 한도와 회복은 JdbcRateLimiter/API 통합 테스트에서 확인한다.
        var counts = new HashMap<String, Integer>();
        when(limiter.tryConsume(eq(RateLimitRule.WITHDRAWAL_REQUESTS), anyString()))
                .thenAnswer(call -> counts.merge(call.getArgument(1), 1, Integer::sum) > 5 ? 60L : 0L);
        var first = new MemberId(UUID.randomUUID());
        var second = new MemberId(UUID.randomUUID());
        var calls = new AtomicInteger();
        // when / then
        for (int i = 0; i < 7; i++) {
            SecurityContextHolder.getContext().setAuthentication(new MemberAuthentication(i == 6 ? second : first));
            var request = new MockHttpServletRequest("POST", "/api/auth/withdrawal");
            request.setRemoteAddr("same-ip");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
            if (i == 5) {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
                assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
                assertThat(response.getContentAsString()).contains("AUTH_TOO_MANY_REQUESTS");
            } else assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(calls).hasValue(6);
    }
}
