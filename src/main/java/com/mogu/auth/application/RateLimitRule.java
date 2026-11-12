package com.mogu.auth.application;

public enum RateLimitRule {
    LOGIN_REQUESTS,
    LOGIN_FAILURES,
    NONCE_REQUESTS,
    REFRESH_REQUESTS,
    REFRESH_FAILURES,
    LOGOUT_REQUESTS,
    IDENTITY_REQUESTS,
    IDENTITY_FAILURES;

    public String ruleName() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
