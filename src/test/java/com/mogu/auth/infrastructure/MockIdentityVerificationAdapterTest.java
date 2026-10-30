package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MockIdentityVerificationAdapterTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-30T00:00:00.000000999Z", "2026-10-30T00:00:00.999999999Z"})
    @DisplayName("나노초 시각으로 생성한 인증을 DB에서 조회해도 인증 시각이 현재보다 미래가 되지 않는다")
    void fetchedVerificationTimeDoesNotExceedClock(String instant) {
        // given
        var clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        var jdbc = JdbcClient.create(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        var adapter = new MockIdentityVerificationAdapter(jdbc, clock, "");

        // when
        var verificationId = adapter.create(UUID.randomUUID(), "01012345678", "VERIFIED");
        var verified = adapter.fetch(verificationId);

        // then
        assertThat(verified.verifiedAt()).isBeforeOrEqualTo(clock.instant());
        assertThat(verified.verifiedAt()).isEqualTo(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
