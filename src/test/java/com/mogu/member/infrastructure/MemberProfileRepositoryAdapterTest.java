package com.mogu.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.member.application.MemberProfileService;
import com.mogu.member.application.UpdateMyProfileCommand;
import com.mogu.member.domain.Bio;
import com.mogu.member.domain.MemberErrorCode;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.Nickname;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    @Test
    @DisplayName("수정한 모든 필드가 저장되며 조회 엔티티는 신규가 아니다")
    void updatesExistingProfile() {
        // given
        MemberId id = createProfile();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String nickname = nickname();
        // when
        transactions.executeWithoutResult(status -> {
            MemberProfile profile = adapter.findByIdForUpdate(id).orElseThrow();
            profile.updateProfile(Nickname.of(nickname), Bio.of("😀".repeat(50)), now);
            adapter.update(profile);
        });
        MemberProfileJpaEntity stored = repository.findById(id.value()).orElseThrow();
        // then
        assertThat(stored.isNew()).isFalse();
        assertThat(stored.getNickname()).isEqualTo(nickname);
        assertThat(stored.getNicknameKey()).isEqualTo(nickname.toLowerCase(Locale.ROOT));
        assertThat(stored.getBio()).isEqualTo("😀".repeat(50));
        assertThat(stored.getNicknameChangedAt()).isNull();
        assertThat(stored.getUpdatedAt()).isEqualTo(now);
        transactions.executeWithoutResult(status -> {
            MemberProfile profile = adapter.findByIdForUpdate(id).orElseThrow();
            profile.updateProfile(Nickname.of(nickname()), Bio.of(null), now.plusSeconds(1));
            adapter.update(profile);
        });
        assertThat(adapter.findById(id).orElseThrow().nicknameChangedAt()).isEqualTo(now.plusSeconds(1));
    }

    @Test
    @DisplayName("수정 대상이 없으면 오류를 내며 새 행을 삽입하지 않는다")
    void neverInsertsOnUpdate() {
        // given
        MemberId id = new MemberId(UUID.randomUUID());
        MemberProfile profile = MemberProfile.create(id, Instant.now().truncatedTo(ChronoUnit.MICROS));
        // when / then
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> adapter.update(profile)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.PROFILE_NOT_FOUND));
        assertThat(adapter.existsById(id)).isFalse();
    }

    @Test
    @DisplayName("다른 회원의 닉네임을 대소문자만 달리 저장하면 중복 오류로 변환한다")
    void translatesNicknameUniqueViolation() {
        // given
        MemberId first = createProfile();
        MemberId second = createProfile();
        String nickname = nickname();
        updateDirectly(first, nickname);
        // when / then
        assertThatThrownBy(() -> updateDirectly(second, nickname.toUpperCase(Locale.ROOT)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.DUPLICATE_NICKNAME));
        assertThat(adapter.findById(second).orElseThrow().isCompleted()).isFalse();
    }

    @Test
    @DisplayName("두 회원의 동시 닉네임 저장은 한 명만 성공한다")
    void serializesCompetingNicknameClaims() throws Exception {
        // given
        MemberId first = createProfile();
        MemberId second = createProfile();
        String nickname = nickname();
        CountDownLatch ready = new CountDownLatch(2);
        // when
        List<String> results =
                concurrently(() -> claimNickname(first, nickname, ready), () -> claimNickname(second, nickname, ready));
        // then
        assertThat(results).containsExactlyInAnyOrder("success", "MEMBER_DUPLICATE_NICKNAME");
        assertThat(List.of(first, second).stream()
                        .filter(id -> adapter.findById(id).orElseThrow().isCompleted())
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 회원의 동시 변경은 행 잠금으로 한 번만 반영한다")
    void serializesSameMemberChanges() throws Exception {
        // given
        MemberId id = createProfile();
        updateDirectly(id, nickname());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberProfileService service = new MemberProfileService(adapter, Clock.fixed(now, ZoneOffset.UTC));
        CountDownLatch ready = new CountDownLatch(2);
        String first = nickname();
        String second = nickname();
        // when
        List<String> results = concurrently(
                () -> changeNickname(service, id, first, ready), () -> changeNickname(service, id, second, ready));
        // then
        assertThat(results).containsExactlyInAnyOrder("success", "MEMBER_NICKNAME_CHANGE_TOO_SOON");
        MemberProfile stored = adapter.findById(id).orElseThrow();
        assertThat(stored.nickname().value()).isIn(first, second);
        assertThat(stored.nicknameChangedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("닉네임 제약 외의 수정 저장 실패는 원래 예외를 유지하고 롤백한다")
    void preservesUnrelatedUpdateFailure() {
        // given
        MemberId id = createProfile();
        Instant outOfRange = Instant.parse("+300000-01-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS);
        String nickname = nickname();
        // when / then
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    MemberProfile profile = adapter.findByIdForUpdate(id).orElseThrow();
                    profile.updateProfile(Nickname.of(nickname), Bio.of(null), outOfRange);
                    adapter.update(profile);
                }))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(adapter.findById(id).orElseThrow().isCompleted()).isFalse();
    }

    private String claimNickname(MemberId id, String nickname, CountDownLatch ready) {
        return outcome(() -> transactions.executeWithoutResult(status -> {
            MemberProfile profile = adapter.findByIdForUpdate(id).orElseThrow();
            assertThat(adapter.existsByNicknameKeyAndMemberIdNot(
                            Nickname.of(nickname).key(), id))
                    .isFalse();
            profile.updateProfile(
                    Nickname.of(nickname), Bio.of(null), Instant.now().truncatedTo(ChronoUnit.MICROS));
            awaitBoth(ready);
            adapter.update(profile);
        }));
    }

    private String changeNickname(MemberProfileService service, MemberId id, String nickname, CountDownLatch ready) {
        return outcome(() -> transactions.executeWithoutResult(status -> {
            awaitBoth(ready);
            service.updateMyProfile(new UpdateMyProfileCommand(id, nickname, null));
        }));
    }

    private String outcome(Runnable action) {
        try {
            action.run();
            return "success";
        } catch (BusinessException exception) {
            return exception.errorCode().code();
        }
    }

    private void awaitBoth(CountDownLatch ready) {
        ready.countDown();
        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private List<String> concurrently(Callable<String> first, Callable<String> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(first);
            var secondResult = executor.submit(second);
            return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
        }
    }

    private MemberId createProfile() {
        MemberId id = new MemberId(UUID.randomUUID());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        transactions.executeWithoutResult(status -> adapter.save(MemberProfile.create(id, now), now));
        return id;
    }

    private String nickname() {
        return "N" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }

    private void updateDirectly(MemberId id, String nickname) {
        transactions.executeWithoutResult(status -> {
            MemberProfile profile = adapter.findByIdForUpdate(id).orElseThrow();
            profile.updateProfile(
                    Nickname.of(nickname), Bio.of(null), Instant.now().truncatedTo(ChronoUnit.MICROS));
            adapter.update(profile);
        });
    }
}
