package com.mogu.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.auth.MemberId;
import com.mogu.member.domain.MemberProfile;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MemberProfileRepositoryAdapterTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    private final MemberProfileRepositoryAdapter adapter;
    private final JpaMemberProfileRepository repository;
    private final MemberProfileMapper mapper;
    private final TransactionTemplate transactions;

    @Autowired
    MemberProfileRepositoryAdapterTest(
            MemberProfileRepositoryAdapter adapter,
            JpaMemberProfileRepository repository,
            MemberProfileMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.adapter = adapter;
        this.repository = repository;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("저장 후 새 트랜잭션에서 식별자와 가입 및 저장 시각을 조회한다")
    void persistsProfileWithoutChangingValues() {
        // given
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant createdAt = registeredAt.plusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        MemberProfile profile = MemberProfile.create(memberId, registeredAt);

        // when
        transactions.executeWithoutResult(status -> adapter.save(profile, createdAt));
        MemberProfileJpaEntity stored = repository.findById(memberId.value()).orElseThrow();
        MemberProfile restored = mapper.toDomain(stored);

        // then
        assertThat(adapter.existsById(memberId)).isTrue();
        assertThat(restored.memberId()).isEqualTo(memberId);
        assertThat(restored.registeredAt()).isEqualTo(registeredAt);
        assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("같은 회원을 다시 저장하면 기본 키 제약으로 실패하고 기존 값을 유지한다")
    void rejectsDuplicateInsertWithoutOverwriting() {
        // given
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant createdAt = registeredAt.plusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        transactions.executeWithoutResult(
                status -> adapter.save(MemberProfile.create(memberId, registeredAt), createdAt));
        MemberProfile duplicate = MemberProfile.create(memberId, registeredAt.plusSeconds(60));

        // when / then
        assertThatThrownBy(() ->
                        transactions.executeWithoutResult(status -> adapter.save(duplicate, createdAt.plusSeconds(60))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(
                        SQLException.class,
                        cause -> assertThat(cause.getSQLState()).isEqualTo("23505"));
        MemberProfileJpaEntity stored = repository.findById(memberId.value()).orElseThrow();
        assertThat(stored.getId()).isEqualTo(memberId.value());
        assertThat(stored.getRegisteredAt()).isEqualTo(registeredAt);
        assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
    }
}
