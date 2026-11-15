package com.mogu.auth.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.auth.withdrawal")
public record WithdrawalProperties(Duration rejoinRestriction) {
    public WithdrawalProperties {
        rejoinRestriction = rejoinRestriction == null ? Duration.ofDays(30) : rejoinRestriction;
        if (rejoinRestriction.isNegative() || rejoinRestriction.isZero())
            throw new IllegalArgumentException("mogu.auth.withdrawal.rejoin-restriction");
    }
}
