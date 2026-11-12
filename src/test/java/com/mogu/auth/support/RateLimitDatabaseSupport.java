package com.mogu.auth.support;

import com.mogu.auth.application.RateLimitRule;
import com.mogu.auth.infrastructure.JdbcRateLimiter;
import com.mogu.auth.infrastructure.RateLimitProperties;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
public abstract class RateLimitDatabaseSupport {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    protected final MutableAuthClock clock = new MutableAuthClock();
    protected JdbcTemplate jdbc;
    protected JdbcRateLimiter limiter;

    public static RateLimitProperties properties() {
        return new RateLimitProperties(
                Duration.ofMillis(200),
                Arrays.stream(RateLimitRule.values())
                        .collect(Collectors.toMap(
                                RateLimitRule::ruleName,
                                rule -> new RateLimitProperties.Rule(
                                        10, Duration.ofMinutes(1), Duration.ofMinutes(15)))));
    }

    @BeforeEach
    void initialize() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute(
                "DROP TABLE IF EXISTS auth_rate_limit_window, auth_rate_limit_bucket," + " auth_rate_limit_streak");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V202611120959__create_auth_rate_limit.sql"))
                .execute(source);
        limiter = new JdbcRateLimiter(source, properties(), clock);
    }
}
