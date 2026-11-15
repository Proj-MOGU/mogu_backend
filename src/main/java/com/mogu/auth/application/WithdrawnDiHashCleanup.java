package com.mogu.auth.application;

import com.mogu.auth.domain.AuthAccountRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WithdrawnDiHashCleanup {
    private final AuthAccountRepository accounts;
    private final WithdrawalPolicy properties;
    private final Clock clock;

    public WithdrawnDiHashCleanup(
            AuthAccountRepository accounts, WithdrawalPolicy properties, @Qualifier("authClock") Clock clock) {
        this.accounts = accounts;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${mogu.auth.withdrawal.cleanup-interval:PT6H}",
            initialDelayString = "${mogu.auth.withdrawal.cleanup-interval:PT6H}")
    @Transactional
    public void cleanup() {
        accounts.clearExpiredWithdrawnDiHashes(
                clock.instant().minus(properties.rejoinRestriction()).truncatedTo(ChronoUnit.MICROS));
    }
}
