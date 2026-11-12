package com.mogu.auth.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RateLimitCleanup {
    private final RateLimiter limiter;

    public RateLimitCleanup(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Scheduled(
            fixedDelayString = "${mogu.auth.rate-limit.cleanup-interval:PT10M}",
            initialDelayString = "${mogu.auth.rate-limit.cleanup-interval:PT10M}")
    public void cleanup() {
        limiter.cleanup();
    }
}
