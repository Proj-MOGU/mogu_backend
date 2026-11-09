package com.mogu.auth.application;

import com.mogu.auth.domain.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefreshTokenCleanup {
    private final RefreshTokenRepository tokens;
    private final Clock clock;

    public RefreshTokenCleanup(RefreshTokenRepository tokens, @Qualifier("authClock") Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${mogu.auth.refresh-token.cleanup-interval:PT6H}",
            initialDelayString = "${mogu.auth.refresh-token.cleanup-interval:PT6H}")
    @Transactional
    public void cleanup() {
        tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)).truncatedTo(ChronoUnit.MICROS));
    }
}
