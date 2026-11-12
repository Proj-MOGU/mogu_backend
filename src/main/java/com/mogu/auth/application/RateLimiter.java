package com.mogu.auth.application;

public interface RateLimiter {
    /** 0이면 허용, 양수이면 재시도까지 남은 초(올림). */
    long tryConsume(RateLimitRule rule, String key);

    long blockedFor(RateLimitRule rule, String key);

    void recordFailure(RateLimitRule rule, String key);

    void recordSuccess(RateLimitRule rule, String key);

    void cleanup();
}
