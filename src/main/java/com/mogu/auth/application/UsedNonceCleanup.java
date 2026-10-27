package com.mogu.auth.application;

import com.mogu.auth.domain.UsedNonceRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UsedNonceCleanup {
    private final UsedNonceRepository repository;
    private final Clock clock;

    public UsedNonceCleanup(UsedNonceRepository repository, @Qualifier("authClock") Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${mogu.auth.nonce.cleanup-interval:PT1H}",
            initialDelayString = "${mogu.auth.nonce.cleanup-interval:PT1H}")
    @Transactional
    public void cleanup() {
        repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
    }
}
