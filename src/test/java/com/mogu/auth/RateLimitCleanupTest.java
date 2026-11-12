package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.RateLimitCleanup;
import com.mogu.auth.support.RateLimitDatabaseSupport;
import java.sql.Timestamp;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimitCleanupTest extends RateLimitDatabaseSupport {
    @Test
    @DisplayName("보존 기간보다 오래된 행만 삭제하고 경계와 최신 행은 보존한다")
    void deletesOnlyOlderRows() {
        // given
        for (int offset : new int[] {-1, 0, 1}) {
            var twoDays =
                    Timestamp.from(clock.instant().minus(Duration.ofDays(2)).plusNanos(offset * 1000L));
            var oneDay =
                    Timestamp.from(clock.instant().minus(Duration.ofDays(1)).plusNanos(offset * 1000L));
            jdbc.update(
                    "INSERT INTO auth_rate_limit_window VALUES ('nonce-requests', ?, ?, 1)", "key" + offset, twoDays);
            jdbc.update(
                    "INSERT INTO auth_rate_limit_bucket VALUES ('login-failures', ?, 0, ?)", "key" + offset, twoDays);
            jdbc.update(
                    "INSERT INTO auth_rate_limit_streak VALUES ('identity-failures', ?, 1, ?)", "key" + offset, oneDay);
        }
        // when
        new RateLimitCleanup(limiter).cleanup();
        // then
        for (String table : new String[] {"window", "bucket", "streak"}) {
            assertThat(jdbc.queryForList("SELECT limit_key FROM auth_rate_limit_" + table, String.class))
                    .containsExactlyInAnyOrder("key0", "key1");
        }
    }
}
