package com.mogu.auth.domain;

import java.time.Instant;

public interface UsedNonceRepository {
    /** 원자적으로 사용 기록을 추가하며 이미 사용된 해시는 false를 반환한다. */
    boolean markUsed(String nonceHash, Instant expiresAt);

    int deleteExpiredBefore(Instant threshold);
}
