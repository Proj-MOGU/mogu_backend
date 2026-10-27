package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.UsedNonceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 상태 전이나 비즈니스 규칙이 없는 사용 기록이므로 Domain Model과 JPA Entity를 만들지 않는다. */
@Repository
public class JdbcUsedNonceRepository implements UsedNonceRepository {
    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcUsedNonceRepository(JdbcClient jdbc, @Qualifier("authClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean markUsed(String nonceHash, Instant expiresAt) {
        return jdbc.sql("INSERT INTO auth_used_nonce (nonce_hash, expires_at, used_at) VALUES (:hash, :expiry,"
                                + " :used) ON CONFLICT (nonce_hash) DO NOTHING")
                        .param("hash", nonceHash)
                        .param("expiry", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                        .param("used", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                        .update()
                == 1;
    }

    @Override
    public int deleteExpiredBefore(Instant threshold) {
        return jdbc.sql("DELETE FROM auth_used_nonce WHERE expires_at < :threshold")
                .param("threshold", OffsetDateTime.ofInstant(threshold, ZoneOffset.UTC))
                .update();
    }
}
