package com.mogu.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.common.auth.MemberId;
import com.mogu.common.auth.WithLoginMember;
import com.mogu.common.error.BusinessException;
import com.mogu.common.event.MemberWithdrawn;
import com.mogu.member.domain.Bio;
import com.mogu.member.domain.MemberErrorCode;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.MemberProfileRepository;
import com.mogu.member.infrastructure.JpaMemberProfileRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MemberWithdrawnEventListenerIntegrationTest.ClockConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class MemberWithdrawnEventListenerIntegrationTest {
    private static final String API_MEMBER_ID = "b74393cb-270c-46de-b148-c0988b239001";
    private static final Instant NOW = Instant.parse("2026-11-15T04:00:00.123456Z");
    private static final Duration HOLD_PERIOD = Duration.ofDays(30);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    private final ApplicationEventPublisher publisher;
    private final MemberProfileService service;
    private final MemberProfileRepository profiles;
    private final JpaMemberProfileRepository jpaProfiles;
    private final EntityManager entityManager;
    private final JdbcClient jdbc;
    private final MockMvc mvc;
    private final MutableMemberClock clock;
    private final TransactionTemplate transactions;

    @Autowired
    MemberWithdrawnEventListenerIntegrationTest(
            ApplicationEventPublisher publisher,
            MemberProfileService service,
            MemberProfileRepository profiles,
            JpaMemberProfileRepository jpaProfiles,
            EntityManager entityManager,
            JdbcClient jdbc,
            MockMvc mvc,
            @Qualifier("memberClock") MutableMemberClock clock,
            PlatformTransactionManager transactionManager) {
        this.publisher = publisher;
        this.service = service;
        this.profiles = profiles;
        this.jpaProfiles = jpaProfiles;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
        this.mvc = mvc;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void resetClockAndApiMember() {
        clock.set(NOW);
        jdbc.sql("DELETE FROM member_profile WHERE member_id = :id")
                .param("id", UUID.fromString(API_MEMBER_ID))
                .update();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        @Primary
        @Qualifier("memberClock")
        MutableMemberClock mutableMemberClock() {
            return new MutableMemberClock();
        }
    }

    static final class MutableMemberClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    @DisplayName("완성 프로필 탈퇴는 행을 삭제하고 이벤트 시각부터 30일을 마이크로초로 묶는다")
    void deletesCompletedProfileAndHoldsNickname() {
        // given
        String nickname = nickname();
        MemberId memberId = createProfile(nickname);
        Instant withdrawnAt = NOW.minusSeconds(60).plusNanos(789);

        // when
        withdraw(memberId, withdrawnAt);

        // then
        assertThat(countProfiles(memberId)).isZero();
        assertThat(readRelease(nickname))
                .isEqualTo(withdrawnAt.plus(HOLD_PERIOD).truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("미완성 프로필은 삭제하며 닉네임 묶음을 만들지 않는다")
    void deletesIncompleteProfileWithoutHold() {
        // given
        MemberId memberId = createProfile(null);
        long holdsBefore = countHolds();

        // when
        withdraw(memberId, NOW);

        // then
        assertThat(countProfiles(memberId)).isZero();
        assertThat(countHolds()).isEqualTo(holdsBefore);
    }

    @Test
    @DisplayName("프로필이 없는 회원의 탈퇴는 예외와 변경 없이 끝난다")
    void ignoresMissingProfile() {
        // given
        MemberId missing = new MemberId(UUID.randomUUID());
        long holdsBefore = countHolds();

        // when / then
        assertThatCode(() -> withdraw(missing, NOW)).doesNotThrowAnyException();
        assertThat(countProfiles(missing)).isZero();
        assertThat(countHolds()).isEqualTo(holdsBefore);
    }

    @Test
    @DisplayName("같은 트랜잭션과 커밋 뒤 중복 탈퇴 이벤트는 묶음을 바꾸지 않는다")
    void ignoresDuplicateEvents() {
        // given
        String nickname = nickname();
        MemberId memberId = createProfile(nickname);
        MemberWithdrawn event = new MemberWithdrawn(memberId, NOW);

        // when
        transactions.executeWithoutResult(status -> {
            publisher.publishEvent(event);
            publisher.publishEvent(event);
        });
        assertThatCode(() -> withdraw(memberId, NOW.plusSeconds(60))).doesNotThrowAnyException();

        // then
        assertThat(countProfiles(memberId)).isZero();
        assertThat(countHolds(nickname)).isEqualTo(1);
        assertThat(readRelease(nickname)).isEqualTo(NOW.plus(HOLD_PERIOD));
    }

    @Test
    @DisplayName("발행 트랜잭션 롤백은 프로필 삭제와 닉네임 묶음을 모두 취소한다")
    void rollsBackWithPublishingTransaction() {
        // given
        String nickname = nickname();
        MemberId memberId = createProfile(nickname);

        // when
        transactions.executeWithoutResult(status -> {
            publisher.publishEvent(new MemberWithdrawn(memberId, NOW));
            assertThat(countProfiles(memberId)).isZero();
            assertThat(countHolds(nickname)).isEqualTo(1);
            status.setRollbackOnly();
        });

        // then
        assertThat(countProfiles(memberId)).isEqualTo(1);
        assertThat(countHolds(nickname)).isZero();
    }

    @Test
    @WithLoginMember(API_MEMBER_ID)
    @DisplayName("묶인 닉네임은 원문과 대소문자 및 앞뒤 공백 변형 모두 같은 중복 오류다")
    void rejectsHeldNicknameThroughApi() throws Exception {
        // given
        String nickname = nickname();
        withdraw(createProfile(nickname), NOW);
        createApiProfile();

        // when / then
        for (String input : new String[] {nickname, nickname.toLowerCase(Locale.ROOT), "  " + nickname + "  "}) {
            updateApi(input)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_NICKNAME"));
        }
        assertThat(profiles.findById(MemberId.from(API_MEMBER_ID)).orElseThrow().isCompleted())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    @WithLoginMember(API_MEMBER_ID)
    @DisplayName("해제 시각과 그 이후에는 묶였던 닉네임을 사용할 수 있다")
    void acceptsNicknameAtAndAfterRelease(long secondsAfterRelease) throws Exception {
        // given
        String nickname = nickname();
        withdraw(createProfile(nickname), NOW);
        createApiProfile();
        clock.set(NOW.plus(HOLD_PERIOD).plusSeconds(secondsAfterRelease));

        // when / then
        updateApi(nickname)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname));
    }

    @Test
    @DisplayName("만료된 옛 묶음이 있는 닉네임을 쓰던 회원이 탈퇴하면 새 해제 시각으로 덮어쓴다")
    void overwritesExpiredHoldOnLaterWithdrawal() {
        // given
        String nickname = nickname();
        withdraw(createProfile(nickname), NOW);
        Instant nextWithdrawal = NOW.plus(HOLD_PERIOD).plusSeconds(1);
        clock.set(nextWithdrawal);
        MemberId nextOwner = createProfile(nickname);

        // when
        withdraw(nextOwner, nextWithdrawal);

        // then
        assertThat(countProfiles(nextOwner)).isZero();
        assertThat(countHolds(nickname)).isEqualTo(1);
        assertThat(readRelease(nickname)).isEqualTo(nextWithdrawal.plus(HOLD_PERIOD));
    }

    @Test
    @DisplayName("먼저 읽은 프로필도 다른 트랜잭션의 닉네임 변경 커밋 뒤 최신 키로 묶는다")
    void refreshesPreviouslyManagedProfileBeforeWithdrawal() throws Exception {
        // given
        String oldNickname = nickname();
        String newNickname = nickname();
        MemberId memberId = createProfile(oldNickname);
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch changedCommitted = new CountDownLatch(1);

        // when
        try (var executor = Executors.newSingleThreadExecutor()) {
            var change = executor.submit(() -> {
                await(firstRead);
                try {
                    service.updateMyProfile(new UpdateMyProfileCommand(memberId, newNickname, null));
                } finally {
                    changedCommitted.countDown();
                }
            });
            transactions.executeWithoutResult(status -> {
                var stale = jpaProfiles.findById(memberId.value()).orElseThrow();
                assertThat(stale.getNickname()).isEqualTo(oldNickname);
                firstRead.countDown();
                await(changedCommitted);
                publisher.publishEvent(new MemberWithdrawn(memberId, NOW));
            });
            change.get(20, TimeUnit.SECONDS);
        }

        // then
        assertThat(countProfiles(memberId)).isZero();
        assertThat(countHolds(oldNickname)).isZero();
        assertThat(readRelease(newNickname)).isEqualTo(NOW.plus(HOLD_PERIOD));
    }

    @Test
    @WithLoginMember(API_MEMBER_ID)
    @DisplayName("탈퇴 삭제 커밋 뒤 프로필 수정은 404이며 행이 다시 생기지 않는다")
    void rejectsUpdateAfterWithdrawalWithoutReinserting() throws Exception {
        // given
        MemberId memberId = createApiProfile();
        service.updateMyProfile(new UpdateMyProfileCommand(memberId, nickname(), null));
        withdraw(memberId, NOW);

        // when / then
        updateApi(nickname())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_PROFILE_NOT_FOUND"));
        assertThat(countProfiles(memberId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("탈퇴와 다른 회원의 수정이 겹쳐도 탈퇴 커밋 전후 모두 닉네임 획득을 거절한다")
    void rejectsConcurrentClaimBeforeAndAfterWithdrawalCommit(boolean commitBeforeCheck) throws Exception {
        // given
        String nickname = nickname();
        MemberId withdrawing = createProfile(nickname);
        MemberId claimant = createProfile(null);
        CountDownLatch claimantStarted = new CountDownLatch(1);
        CountDownLatch withdrawalProcessed = new CountDownLatch(1);
        CountDownLatch withdrawalCommitted = new CountDownLatch(1);
        CountDownLatch claimFinished = new CountDownLatch(1);

        // when
        try (var executor = Executors.newFixedThreadPool(2)) {
            var withdrawal = executor.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    publisher.publishEvent(new MemberWithdrawn(withdrawing, NOW));
                    withdrawalProcessed.countDown();
                    await(claimantStarted);
                    if (!commitBeforeCheck) await(claimFinished);
                });
                withdrawalCommitted.countDown();
            });
            var claim = executor.submit(() -> {
                try {
                    assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                                profiles.findByIdForUpdate(claimant).orElseThrow();
                                claimantStarted.countDown();
                                await(withdrawalProcessed);
                                if (commitBeforeCheck) await(withdrawalCommitted);
                                service.updateMyProfile(new UpdateMyProfileCommand(claimant, nickname, null));
                            }))
                            .isInstanceOfSatisfying(
                                    BusinessException.class,
                                    error -> assertThat(error.errorCode())
                                            .isEqualTo(MemberErrorCode.DUPLICATE_NICKNAME));
                } finally {
                    claimFinished.countDown();
                }
            });
            claim.get(20, TimeUnit.SECONDS);
            withdrawal.get(20, TimeUnit.SECONDS);
        }

        // then
        assertThat(countProfiles(withdrawing)).isZero();
        assertThat(profiles.findById(claimant).orElseThrow().isCompleted()).isFalse();
        assertThat(readRelease(nickname)).isEqualTo(NOW.plus(HOLD_PERIOD));
    }

    @Test
    @DisplayName("같은 트랜잭션에 남은 다른 관리 엔티티는 탈퇴 처리 뒤에도 변경을 저장한다")
    void preservesOtherManagedEntitiesInPublishingTransaction() {
        // given
        MemberId withdrawing = createProfile(nickname());
        MemberId other = createProfile(nickname());

        // when
        transactions.executeWithoutResult(status -> {
            var entity = jpaProfiles.findById(other.value()).orElseThrow();
            publisher.publishEvent(new MemberWithdrawn(withdrawing, NOW));
            assertThat(entityManager.contains(entity)).isTrue();
            MemberProfile profile = profiles.findById(other).orElseThrow();
            profile.updateProfile(profile.nickname(), Bio.of("소개 변경"), NOW);
            profiles.update(profile);
        });

        // then
        assertThat(profiles.findById(other).orElseThrow().bio().value()).isEqualTo("소개 변경");
    }

    @Test
    @DisplayName("오래된 프로필을 읽은 트랜잭션도 탈퇴 커밋 뒤 저장하면 404이며 행을 되살리지 않는다")
    void rejectsStaleProfileSaveAfterWithdrawal() throws Exception {
        // given
        MemberId memberId = createProfile(nickname());
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch withdrawn = new CountDownLatch(1);

        // when / then
        try (var executor = Executors.newSingleThreadExecutor()) {
            var withdrawal = executor.submit(() -> {
                await(firstRead);
                try {
                    withdraw(memberId, NOW);
                } finally {
                    withdrawn.countDown();
                }
            });
            assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                        MemberProfile stale = profiles.findById(memberId).orElseThrow();
                        firstRead.countDown();
                        await(withdrawn);
                        stale.updateProfile(stale.nickname(), Bio.of("오래된 수정"), NOW);
                        profiles.update(stale);
                    }))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.PROFILE_NOT_FOUND));
            withdrawal.get(20, TimeUnit.SECONDS);
        }
        assertThat(countProfiles(memberId)).isZero();
    }

    private MemberId createProfile(String nickname) {
        MemberId memberId = new MemberId(UUID.randomUUID());
        service.createIncompleteProfile(new CreateMemberProfileCommand(memberId, NOW.minusSeconds(60)));
        if (nickname != null) service.updateMyProfile(new UpdateMyProfileCommand(memberId, nickname, null));
        return memberId;
    }

    private MemberId createApiProfile() {
        MemberId memberId = MemberId.from(API_MEMBER_ID);
        service.createIncompleteProfile(new CreateMemberProfileCommand(memberId, NOW.minusSeconds(60)));
        return memberId;
    }

    private void withdraw(MemberId memberId, Instant withdrawnAt) {
        transactions.executeWithoutResult(status -> publisher.publishEvent(new MemberWithdrawn(memberId, withdrawnAt)));
    }

    private ResultActions updateApi(String nickname) throws Exception {
        return mvc.perform(put("/api/members/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"));
    }

    private long countProfiles(MemberId memberId) {
        return jdbc.sql("SELECT count(*) FROM member_profile WHERE member_id = :id")
                .param("id", memberId.value())
                .query(Long.class)
                .single();
    }

    private long countHolds() {
        return jdbc.sql("SELECT count(*) FROM member_nickname_hold")
                .query(Long.class)
                .single();
    }

    private long countHolds(String nickname) {
        return jdbc.sql("SELECT count(*) FROM member_nickname_hold WHERE nickname_key = :key")
                .param("key", nickname.toLowerCase(Locale.ROOT))
                .query(Long.class)
                .single();
    }

    private Instant readRelease(String nickname) {
        return jdbc.sql("SELECT released_at FROM member_nickname_hold WHERE nickname_key = :key")
                .param("key", nickname.toLowerCase(Locale.ROOT))
                .query((rs, rowNum) -> rs.getTimestamp(1).toInstant())
                .single();
    }

    private String nickname() {
        return "N" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
