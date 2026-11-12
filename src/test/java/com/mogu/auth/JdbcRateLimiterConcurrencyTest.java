package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.RateLimitRule;
import com.mogu.auth.support.RateLimitDatabaseSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcRateLimiterConcurrencyTest extends RateLimitDatabaseSupport {
    @Test
    @DisplayName("같은 키의 동시 요청 50개 중 정확히 10개만 허용하고 초과 쓰기는 없다")
    void admitsExactlyTenConcurrentRequests() throws Exception {
        // given / when
        var results = concurrent(() -> limiter.tryConsume(RateLimitRule.NONCE_REQUESTS, "::1"));
        // then
        assertThat(results.stream().filter(value -> value == 0).count()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT request_count FROM auth_rate_limit_window", Integer.class))
                .isEqualTo(10);
    }

    @Test
    @DisplayName("동시 실패 기록은 버킷을 음수로 만들지 않고 회복 구간의 나머지를 보존한다")
    void concurrentFailuresStopAtZeroAndRefill() throws Exception {
        // given / when
        concurrent(() -> {
            limiter.recordFailure(RateLimitRule.LOGIN_FAILURES, "::1");
            return 0L;
        });
        // then
        assertThat(jdbc.queryForObject("SELECT tokens FROM auth_rate_limit_bucket", Integer.class))
                .isZero();
        assertThat(limiter.blockedFor(RateLimitRule.LOGIN_FAILURES, "::1")).isEqualTo(900);
        clock.advance(Duration.ofMinutes(14));
        assertThat(limiter.blockedFor(RateLimitRule.LOGIN_FAILURES, "::1")).isEqualTo(60);
        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.blockedFor(RateLimitRule.LOGIN_FAILURES, "::1")).isZero();
        limiter.recordFailure(RateLimitRule.LOGIN_FAILURES, "::1");
        assertThat(limiter.blockedFor(RateLimitRule.LOGIN_FAILURES, "::1")).isEqualTo(900);
        clock.advance(Duration.ofDays(1));
        limiter.recordFailure(RateLimitRule.LOGIN_FAILURES, "::1");
        assertThat(jdbc.queryForObject("SELECT tokens FROM auth_rate_limit_bucket", Integer.class))
                .isEqualTo(9);
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    @DisplayName("테이블 잠금으로 200ms를 초과한 쿼리는 취소되고 허용하며 WARN 한 줄을 남긴다")
    void queryTimeoutFailsOpen() {
        // given
        var logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(com.mogu.auth.infrastructure.JdbcRateLimiter.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.execute("LOCK TABLE auth_rate_limit_window IN ACCESS EXCLUSIVE MODE");
                    // when / then: 다른 커넥션의 쿼리가 취소되어야 이 잠금 구간을 빠져나온다.
                    assertThat(limiter.tryConsume(RateLimitRule.NONCE_REQUESTS, "::1"))
                            .isZero();
                    assertThat(events.list).hasSize(1);
                    assertThat(events.list.getFirst().getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                    return true;
                } finally {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_rate_limit_window", Integer.class))
                    .isZero();
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    private java.util.List<Long> concurrent(Callable<Long> action) throws Exception {
        var ready = new CountDownLatch(50);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(50)) {
            var futures = new ArrayList<Future<Long>>();
            for (int i = 0; i < 50; i++)
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return action.call();
                }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new ArrayList<Long>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        }
    }
}
