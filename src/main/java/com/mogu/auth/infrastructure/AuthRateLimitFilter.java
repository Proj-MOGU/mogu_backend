package com.mogu.auth.infrastructure;

import com.mogu.auth.application.RateLimitRule;
import com.mogu.auth.application.RateLimiter;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.error.ErrorResponse;
import com.mogu.common.error.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** 보안 체인에서만 생성한다. 서블릿 필터로 자동 등록하지 않는다. */
public final class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private final RateLimiter limiter;
    private final JsonMapper mapper;

    public AuthRateLimitFilter(RateLimiter limiter, JsonMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getServletPath();
        if (path.isEmpty())
            path = request.getRequestURI().substring(request.getContextPath().length());
        RateLimitRule requests;
        RateLimitRule failures = null;
        AuthErrorCode failure = null;
        String key = request.getRemoteAddr();
        switch (path) {
            case "/api/auth/nonce" -> requests = RateLimitRule.NONCE_REQUESTS;
            case "/api/auth/refresh" -> {
                requests = RateLimitRule.REFRESH_REQUESTS;
                failures = RateLimitRule.REFRESH_FAILURES;
                failure = AuthErrorCode.INVALID_REFRESH_TOKEN;
            }
            case "/api/auth/logout" -> requests = RateLimitRule.LOGOUT_REQUESTS;
            case "/api/auth/identity-verification", "/api/auth/mock-identity-verifications" -> {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (!(authentication instanceof PendingRegistrationAuthentication pending)) {
                    chain.doFilter(request, response);
                    return;
                }
                key = pending.getPrincipal().registrationId().toString();
                requests = RateLimitRule.IDENTITY_REQUESTS;
                if (path.equals("/api/auth/identity-verification")) {
                    failures = RateLimitRule.IDENTITY_FAILURES;
                    failure = AuthErrorCode.INVALID_IDENTITY_VERIFICATION;
                }
            }
            default -> {
                if (!path.matches("/api/auth/login/[^/]+")) {
                    chain.doFilter(request, response);
                    return;
                }
                requests = RateLimitRule.LOGIN_REQUESTS;
                failures = RateLimitRule.LOGIN_FAILURES;
                failure = AuthErrorCode.INVALID_SOCIAL_TOKEN;
            }
        }
        if (failures != null && reject(response, failures, key, limiter.blockedFor(failures, key))) return;
        if (reject(response, requests, key, limiter.tryConsume(requests, key))) return;
        chain.doFilter(request, response);
        if (failure != null
                && response.getStatus() == failure.status().value()
                && failure.code().equals(request.getAttribute(GlobalExceptionHandler.ERROR_CODE_ATTRIBUTE))) {
            limiter.recordFailure(failures, key);
        } else if (failures == RateLimitRule.IDENTITY_FAILURES && response.getStatus() == 200) {
            limiter.recordSuccess(failures, key);
        }
    }

    private boolean reject(HttpServletResponse response, RateLimitRule rule, String key, long seconds)
            throws IOException {
        if (seconds <= 0) return false;
        log.warn("Auth rate limit exceeded rule={} key={}", rule.ruleName(), key);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(seconds));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(AuthErrorCode.TOO_MANY_REQUESTS));
        return true;
    }
}
