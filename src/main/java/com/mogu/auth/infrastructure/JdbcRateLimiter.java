package com.mogu.auth.infrastructure;

import com.mogu.auth.application.RateLimitRule;
import com.mogu.auth.application.RateLimiter;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 단일 서버의 PostgreSQL 원자적 카운터. 제한 장치 장애가 로그인 전체 장애로 전파되지 않도록
 * 타임아웃과 DB 오류는 WARN 한 줄을 남기고 fail-open 한다. 비즈니스 트랜잭션 밖의 필터에서 호출한다.
 */
@Component
public class JdbcRateLimiter implements RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(JdbcRateLimiter.class);
    private final NamedParameterJdbcTemplate jdbc;
    private final RateLimitProperties properties;
    private final Clock clock;

    public JdbcRateLimiter(DataSource dataSource, RateLimitProperties properties, @Qualifier("authClock") Clock clock) {
        this.jdbc = new NamedParameterJdbcTemplate(new MillisecondJdbcTemplate(dataSource, properties.queryTimeout()));
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public long tryConsume(RateLimitRule rule, String key) {
        return safely(
                rule,
                () -> {
                    var config = properties.rule(rule);
                    var now = now();
                    long width = config.window().toMillis();
                    var start = Instant.ofEpochMilli(Math.floorDiv(now.toEpochMilli(), width) * width);
                    var params = parameters(rule, key, now);
                    params.put("window", Timestamp.from(start));
                    var rows = jdbc.queryForList("""
                        INSERT INTO auth_rate_limit_window (rule_name, limit_key, window_start, request_count)
                        VALUES (:rule, :key, :window, 1)
                        ON CONFLICT (rule_name, limit_key, window_start) DO UPDATE
                        SET request_count = auth_rate_limit_window.request_count + 1
                        WHERE auth_rate_limit_window.request_count < :limit
                        RETURNING request_count
                        """, params, Integer.class);
                    return rows.isEmpty() ? secondsUntil(now, start.plus(config.window())) : 0L;
                },
                0L);
    }

    @Override
    public long blockedFor(RateLimitRule rule, String key) {
        return safely(
                rule,
                () -> {
                    var now = now();
                    var params = parameters(rule, key, now);
                    String sql;
                    if (rule == RateLimitRule.IDENTITY_FAILURES) {
                        sql = """
                            SELECT CASE WHEN consecutive_failures >= :limit
                                THEN GREATEST(1, CEIL(EXTRACT(EPOCH FROM (updated_at + INTERVAL '1 day' - :now))))
                                ELSE 0 END
                            FROM auth_rate_limit_streak WHERE rule_name = :rule AND limit_key = :key
                            """;
                    } else {
                        sql = """
                            SELECT CASE WHEN tokens + GREATEST(0, FLOOR(EXTRACT(EPOCH FROM
                                (CAST(:now AS timestamptz) - refilled_at)) / :refill)) <= 0
                                THEN GREATEST(1, CEIL(EXTRACT(EPOCH FROM
                                    (refilled_at + :refill * INTERVAL '1 second' - :now)))) ELSE 0 END
                            FROM auth_rate_limit_bucket WHERE rule_name = :rule AND limit_key = :key
                            """;
                    }
                    return jdbc.queryForList(sql, params, Long.class).stream()
                            .findFirst()
                            .orElse(0L);
                },
                0L);
    }

    @Override
    public void recordFailure(RateLimitRule rule, String key) {
        safely(
                rule,
                () -> {
                    var params = parameters(rule, key, now());
                    if (rule == RateLimitRule.IDENTITY_FAILURES) {
                        jdbc.update("""
                            INSERT INTO auth_rate_limit_streak (rule_name, limit_key, consecutive_failures, updated_at)
                            VALUES (:rule, :key, 1, :now)
                            ON CONFLICT (rule_name, limit_key) DO UPDATE
                            SET consecutive_failures = auth_rate_limit_streak.consecutive_failures + 1,
                                updated_at = EXCLUDED.updated_at
                            WHERE auth_rate_limit_streak.consecutive_failures < :limit
                            """, params);
                    } else {
                        // 구간의 나머지를 보존해 실패 빈도가 회복 시각을 늦추지 않게 한다.
                        jdbc.update("""
                            INSERT INTO auth_rate_limit_bucket (rule_name, limit_key, tokens, refilled_at)
                            VALUES (:rule, :key, :limit - 1, :now)
                            ON CONFLICT (rule_name, limit_key) DO UPDATE SET
                                tokens = LEAST(:limit, auth_rate_limit_bucket.tokens + GREATEST(0,
                                    FLOOR(EXTRACT(EPOCH FROM (EXCLUDED.refilled_at - auth_rate_limit_bucket.refilled_at))
                                        / :refill))) - 1,
                                refilled_at = auth_rate_limit_bucket.refilled_at + GREATEST(0,
                                    FLOOR(EXTRACT(EPOCH FROM (EXCLUDED.refilled_at - auth_rate_limit_bucket.refilled_at))
                                        / :refill)) * :refill * INTERVAL '1 second'
                            WHERE auth_rate_limit_bucket.tokens + GREATEST(0,
                                FLOOR(EXTRACT(EPOCH FROM (EXCLUDED.refilled_at - auth_rate_limit_bucket.refilled_at))
                                    / :refill)) > 0
                            """, params);
                    }
                    return true;
                },
                false);
    }

    @Override
    public void recordSuccess(RateLimitRule rule, String key) {
        safely(
                rule,
                () -> {
                    jdbc.update(
                            "DELETE FROM auth_rate_limit_streak WHERE rule_name = :rule AND limit_key = :key",
                            Map.of("rule", rule.ruleName(), "key", key));
                    return true;
                },
                false);
    }

    @Override
    public void cleanup() {
        safely(
                RateLimitRule.NONCE_REQUESTS,
                () -> {
                    var now = now();
                    jdbc.update(
                            """
                            WITH windows AS (
                                DELETE FROM auth_rate_limit_window WHERE window_start < :twoDays
                            ), buckets AS (
                                DELETE FROM auth_rate_limit_bucket WHERE refilled_at < :twoDays
                            )
                            DELETE FROM auth_rate_limit_streak WHERE updated_at < :oneDay
                            """,
                            Map.of(
                                    "twoDays",
                                    Timestamp.from(now.minus(Duration.ofDays(2))),
                                    "oneDay",
                                    Timestamp.from(now.minus(Duration.ofDays(1)))));
                    return true;
                },
                false);
    }

    private Map<String, Object> parameters(RateLimitRule rule, String key, Instant now) {
        var config = properties.rule(rule);
        var params = new HashMap<String, Object>();
        params.put("rule", rule.ruleName());
        params.put("key", key);
        params.put("now", Timestamp.from(now));
        params.put("limit", config.limit());
        if (config.refillInterval() != null)
            params.put("refill", config.refillInterval().toMillis() / 1000.0);
        return params;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static long secondsUntil(Instant now, Instant end) {
        var remaining = Duration.between(now, end);
        return Math.max(1, remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1));
    }

    private <T> T safely(RateLimitRule rule, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (DataAccessException exception) {
            log.warn(
                    "Auth rate limiter unavailable rule={} cause={}",
                    rule.ruleName(),
                    exception.getClass().getSimpleName());
            return fallback;
        }
    }

    /** JDBC 표준은 초 단위이므로 runtimeOnly PostgreSQL 드라이버의 밀리초 API를 사용한다. */
    private static final class MillisecondJdbcTemplate extends JdbcTemplate {
        private final long timeoutMillis;

        private MillisecondJdbcTemplate(DataSource source, Duration timeout) {
            super(source);
            timeoutMillis = timeout.toMillis();
        }

        @Override
        protected void applyStatementSettings(Statement statement) throws SQLException {
            super.applyStatementSettings(statement);
            try {
                Class<?> type = Class.forName("org.postgresql.jdbc.PgStatement");
                Object target = statement.unwrap(type);
                type.getMethod("setQueryTimeoutMs", long.class).invoke(target, timeoutMillis);
            } catch (ReflectiveOperationException exception) {
                Throwable cause = exception instanceof InvocationTargetException invocation
                        ? invocation.getTargetException()
                        : exception;
                throw new SQLException("Cannot configure rate-limit query timeout", cause);
            }
        }
    }
}
