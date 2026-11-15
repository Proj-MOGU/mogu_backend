package com.mogu.auth.infrastructure;

import com.mogu.auth.application.RateLimitRule;
import com.mogu.auth.application.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Bearer 인증 뒤 보안 체인에서만 생성한다. 회원별 탈퇴 요청을 센다. */
public final class WithdrawalRateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter limiter;
    private final JsonMapper mapper;

    public WithdrawalRateLimitFilter(RateLimiter limiter, JsonMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty())
            path = request.getRequestURI().substring(request.getContextPath().length());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if ("POST".equals(request.getMethod())
                && "/api/auth/withdrawal".equals(path)
                && authentication instanceof MemberAuthentication member
                && member.isAuthenticated()) {
            String key = member.getPrincipal().value().toString();
            var rule = RateLimitRule.WITHDRAWAL_REQUESTS;
            if (AuthRateLimitFilter.reject(mapper, response, rule, key, limiter.tryConsume(rule, key))) return;
        }
        chain.doFilter(request, response);
    }
}
