package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mogu.auth.application.RateLimitRule;
import com.mogu.auth.infrastructure.JdbcRateLimiter;
import com.mogu.auth.support.MutableAuthClock;
import com.mogu.auth.support.RateLimitDatabaseSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

class JdbcRateLimiterTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("DB 오류와 타임아웃은 허용하고 연산마다 WARN 한 줄만 기록한다")
    void failsOpenWithOneWarning(boolean timeout) {
        // given
        var source = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw timeout ? new SQLTimeoutException("secret") : new SQLException("secret");
            }

            @Override
            public Connection getConnection(String user, String password) throws SQLException {
                return getConnection();
            }
        };
        var limiter = new JdbcRateLimiter(source, RateLimitDatabaseSupport.properties(), new MutableAuthClock());
        var logger = (Logger) LoggerFactory.getLogger(JdbcRateLimiter.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            // when / then
            assertThat(limiter.tryConsume(RateLimitRule.NONCE_REQUESTS, "127.0.0.1"))
                    .isZero();
            assertThat(events.list).hasSize(1);
            assertThat(limiter.blockedFor(RateLimitRule.LOGIN_FAILURES, "127.0.0.1"))
                    .isZero();
            assertThat(events.list).hasSize(2);
            limiter.recordFailure(RateLimitRule.LOGIN_FAILURES, "127.0.0.1");
            limiter.recordSuccess(RateLimitRule.IDENTITY_FAILURES, "id");
            limiter.cleanup();
            assertThat(events.list).hasSize(5).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).doesNotContain("secret");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }
}
