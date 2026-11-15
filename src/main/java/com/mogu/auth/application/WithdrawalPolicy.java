package com.mogu.auth.application;

import java.time.Duration;
import java.util.Objects;

/** 가입과 정리 배치가 공유하는 재가입 제한 정책. */
public record WithdrawalPolicy(Duration rejoinRestriction) {
    public WithdrawalPolicy {
        Objects.requireNonNull(rejoinRestriction);
        if (rejoinRestriction.isZero() || rejoinRestriction.isNegative())
            throw new IllegalArgumentException("Invalid rejoin restriction");
    }
}
