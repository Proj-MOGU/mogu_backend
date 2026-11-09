package com.mogu.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.auth.MemberId;
import com.mogu.common.event.MemberRegistered;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
@SpringBootTest
@Import(MemberRegisteredEventListenerIntegrationTest.FixedClockConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class MemberRegisteredEventListenerIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    private final Clock memberClock;

    private final ApplicationEventPublisher publisher;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    @Autowired
    MemberRegisteredEventListenerIntegrationTest(
            ApplicationEventPublisher publisher,
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager,
            @Qualifier("memberClock") Clock memberClock) {
        this.memberClock = memberClock;
        this.publisher = publisher;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        @Qualifier("memberClock")
        Clock fixedMemberClock() {
            return Clock.fixed(
                    Instant.parse("2026-11-09T07:00:00.123456Z").truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
        }
    }

    @Test
    @DisplayName("가입 이벤트 발행 트랜잭션이 커밋되면 프로필 한 개가 저장된다")
    void createsProfileOnCommit() {
        // given
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = memberClock.instant().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);

        // when
        transactions.executeWithoutResult(
                status -> publisher.publishEvent(new MemberRegistered(memberId, registeredAt)));

        // then
        assertThat(countProfiles(memberId)).isEqualTo(1);
        assertThat(readTimestamp(memberId, "registered_at")).isEqualTo(registeredAt);
        assertThat(readTimestamp(memberId, "created_at")).isEqualTo(memberClock.instant());
    }

    @Test
    @DisplayName("같은 이벤트를 두 번 발행해도 프로필 한 개만 저장된다")
    void handlesDuplicateEventsInSameTransaction() {
        // given
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = memberClock.instant().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        MemberRegistered event = new MemberRegistered(memberId, registeredAt);

        // when
        transactions.executeWithoutResult(status -> {
            publisher.publishEvent(event);
            publisher.publishEvent(event);
        });

        // then
        assertThat(countProfiles(memberId)).isEqualTo(1);
        assertThat(readTimestamp(memberId, "registered_at")).isEqualTo(registeredAt);
    }

    @Test
    @DisplayName("커밋 후 다른 가입 시각으로 재발행해도 최초 가입 시각을 유지한다")
    void preservesFirstRegistrationAcrossTransactions() {
        // given
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = memberClock.instant().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        transactions.executeWithoutResult(
                status -> publisher.publishEvent(new MemberRegistered(memberId, registeredAt)));

        // when
        transactions.executeWithoutResult(
                status -> publisher.publishEvent(new MemberRegistered(memberId, registeredAt.plusSeconds(30))));

        // then
        assertThat(countProfiles(memberId)).isEqualTo(1);
        assertThat(readTimestamp(memberId, "registered_at")).isEqualTo(registeredAt);
        assertThat(readTimestamp(memberId, "created_at")).isEqualTo(memberClock.instant());
    }

    @Test
    @DisplayName("발행 트랜잭션을 롤백하면 프로필도 함께 롤백된다")
    void rollsBackWithPublishingTransaction() {
        // given
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = memberClock.instant().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);

        // when
        transactions.executeWithoutResult(status -> {
            publisher.publishEvent(new MemberRegistered(memberId, registeredAt));
            assertThat(countProfiles(memberId)).isEqualTo(1);
            status.setRollbackOnly();
        });

        // then
        assertThat(countProfiles(memberId)).isZero();
    }

    @Test
    @DisplayName("프로필 저장 실패는 전파되고 발행 트랜잭션의 이전 저장도 롤백된다")
    void rollsBackPublishingTransactionWhenStorageFails() {
        // given
        MemberId firstMemberId = new MemberId(UUID.randomUUID());
        MemberId failingMemberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = memberClock.instant().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        // PostgreSQL TIMESTAMPTZ의 최대 연도(294276)를 넘어 DB 저장 실패를 유발한다.
        Instant outOfRange = Instant.parse("+300000-01-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS);

        // when / then
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    publisher.publishEvent(new MemberRegistered(firstMemberId, registeredAt));
                    assertThat(countProfiles(firstMemberId)).isEqualTo(1);
                    publisher.publishEvent(new MemberRegistered(failingMemberId, outOfRange));
                }))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countProfiles(firstMemberId)).isZero();
        assertThat(countProfiles(failingMemberId)).isZero();
    }

    private long countProfiles(MemberId memberId) {
        return jdbc.sql("SELECT count(*) FROM member_profile WHERE member_id = :id")
                .param("id", memberId.value())
                .query(Long.class)
                .single();
    }

    private Instant readTimestamp(MemberId memberId, String column) {
        return jdbc.sql("SELECT " + column + " FROM member_profile WHERE member_id = :id")
                .param("id", memberId.value())
                .query((rs, rowNum) -> rs.getTimestamp(1).toInstant())
                .single();
    }
}
