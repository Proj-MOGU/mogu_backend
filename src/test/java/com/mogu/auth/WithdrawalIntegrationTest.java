package com.mogu.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mogu.auth.application.*;
import com.mogu.auth.domain.*;
import com.mogu.auth.support.IdentityIntegrationSupport;
import com.mogu.auth.support.MutableAuthClock;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.common.event.*;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Import(WithdrawalIntegrationTest.Events.class)
class WithdrawalIntegrationTest extends IdentityIntegrationSupport {
    @TestBean(name = "authClock", methodName = "mutableAuthClock", enforceOverride = true)
    private Clock testClock;

    static Clock mutableAuthClock() {
        return new MutableAuthClock();
    }

    private final WithdrawalCompletionService withdrawals;
    private final SocialTokenVerificationService verification;
    private final AuthAccountRepository accounts;
    private final WithdrawnDiHashCleanup cleanup;
    private final ApplicationEventPublisher publisher;
    private final Events events;
    private final TransactionTemplate transactions;

    @Autowired
    WithdrawalIntegrationTest(
            MockMvc mvc,
            JdbcClient jdbc,
            NonceService nonces,
            @Qualifier("authClock") Clock clock,
            JsonMapper json,
            WithdrawalCompletionService withdrawals,
            SocialTokenVerificationService verification,
            AuthAccountRepository accounts,
            WithdrawnDiHashCleanup cleanup,
            ApplicationEventPublisher publisher,
            Events events,
            org.springframework.transaction.PlatformTransactionManager manager) {
        super(mvc, jdbc, nonces, clock, json);
        this.withdrawals = withdrawals;
        this.verification = verification;
        this.accounts = accounts;
        this.cleanup = cleanup;
        this.publisher = publisher;
        this.events = events;
        this.transactions = new TransactionTemplate(manager);
    }

    @BeforeEach
    void resetWithdrawal() {
        ((MutableAuthClock) clock).reset();
        events.withdrawn.clear();
        events.registered.clear();
        events.fail = false;
    }

    private MemberId member() {
        return new MemberId(
                jdbc.sql("SELECT member_id FROM auth_account").query(UUID.class).single());
    }

    private VerifiedSocialIdentity reauth(String subject) throws Exception {
        return verification.verifyToken(AuthProvider.KAKAO, token(subject));
    }

    private AuthAccount load(MemberId id) {
        return transactions.execute(s -> accounts.findByMemberId(id).orElseThrow());
    }

    @Test
    @DisplayName("탈퇴는 DB 개인정보와 소셜 연결 및 활성 토큰을 삭제하고 기존 API를 거절한다")
    void clearsDatabaseAndRejectsApis() throws Exception {
        // given
        var response = register("kakao", "withdraw", PHONE_A);
        var id = member();
        // when
        assertThat(withdrawals.withdraw(id, reauth("withdraw"))).isInstanceOf(WithdrawalOutcome.Withdrawn.class);
        // then
        assertThat(jdbc.sql("SELECT name_enc IS NULL AND phone_enc IS NULL FROM auth_account")
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(count("auth_social_identity")).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM auth_refresh_token WHERE revoked_at IS NULL")
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(events.withdrawn).containsExactly(new MemberWithdrawn(id, clock.instant()));
        assertThat(load(id).diHash()).isNotNull();
        mvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content(json.writeValueAsString(new com.mogu.auth.api.RefreshRequest(
                                response.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me/account")
                        .header(
                                "Authorization",
                                "Bearer " + response.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        events.withdrawn.clear();
        assertThat(withdrawals.withdraw(id, reauth("withdraw"))).isInstanceOf(WithdrawalOutcome.AlreadyWithdrawn.class);
        assertThat(events.withdrawn).isEmpty();
        pending("kakao", "withdraw");
    }

    @Test
    @DisplayName("다른 소셜 계정은 거절하고 동기 리스너 실패 시 nonce와 탈퇴 전체를 롤백한다")
    void rejectionAndRollback() throws Exception {
        // given
        register("kakao", "rollback", PHONE_A);
        var id = member();
        long noncesBefore = count("auth_used_nonce");
        // when / then
        assertThatThrownBy(() -> withdrawals.withdraw(id, reauth("other")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.REAUTHENTICATION_FAILED));
        assertThat(events.withdrawn).isEmpty();
        var reauth = reauth("rollback");
        events.fail = true;
        assertThatThrownBy(() -> withdrawals.withdraw(id, reauth)).isInstanceOf(IllegalStateException.class);
        assertThat(load(id).isWithdrawn()).isFalse();
        assertThat(load(id).name()).isNotNull();
        assertThat(count("auth_social_identity")).isEqualTo(1);
        assertThat(count("auth_used_nonce")).isEqualTo(noncesBefore);
        assertThat(jdbc.sql("SELECT count(*) FROM auth_refresh_token WHERE revoked_at IS NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        events.fail = false;
        assertThat(withdrawals.withdraw(id, reauth)).isInstanceOf(WithdrawalOutcome.Withdrawn.class);
    }

    @Test
    @DisplayName("탈퇴 전에 읽은 ACTIVE 계정 저장은 거절하고 개인정보와 소셜 연결을 복원하지 않는다")
    void rejectsStaleActiveAccountSave() throws Exception {
        // given
        register("kakao", "stale-login", PHONE_A);
        var id = member();
        var stale = load(id).updateSocialEmail(
                        new SocialIdentity(AuthProvider.KAKAO, "stale-login"), "updated@example.com", true);
        assertThat(stale.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(stale.name()).isNotNull();
        assertThat(stale.phoneNumber()).isNotNull();
        assertThat(stale.socialIdentities()).hasSize(1);
        withdrawals.withdraw(id, reauth("stale-login"));
        // when / then
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> accounts.save(stale)))
                .isInstanceOfSatisfying(
                        BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.UNAUTHORIZED));
        assertThat(load(id).status()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(jdbc.sql("SELECT name_enc IS NULL AND phone_enc IS NULL FROM auth_account WHERE member_id = :id")
                        .param("id", id.value())
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(count("auth_social_identity")).isZero();
        assertThat(events.withdrawn).hasSize(1);
    }

    @Test
    @DisplayName("로그인 이메일 갱신은 같은 트랜잭션의 오래된 ACTIVE 엔티티로 탈퇴를 덮어쓰지 못한다")
    void staleManagedLoginCannotRestoreWithdrawnAccount() throws Exception {
        assertManagedStaleSaveRejected(true);
    }

    @Test
    @DisplayName("가입 소셜 연결은 같은 트랜잭션의 오래된 ACTIVE 엔티티로 탈퇴를 덮어쓰지 못한다")
    void staleManagedRegistrationCannotRestoreWithdrawnAccount() throws Exception {
        assertManagedStaleSaveRejected(false);
    }

    private void assertManagedStaleSaveRejected(boolean login) throws Exception {
        // given
        register("kakao", "managed-stale", PHONE_A);
        var id = member();
        var di = load(id).diHash();
        var social = new SocialIdentity(AuthProvider.KAKAO, "managed-stale");
        var reauth = reauth("managed-stale");
        var loaded = new CountDownLatch(1);
        var committed = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var staleSave = executor.submit(() -> transactions.executeWithoutResult(status -> {
                var original =
                        (login ? accounts.findBySocialIdentity(social) : accounts.findByDiHash(di)).orElseThrow();
                assertThat(original.status()).isEqualTo(AccountStatus.ACTIVE);
                assertThat(original.name()).isNotNull();
                assertThat(original.phoneNumber()).isNotNull();
                assertThat(original.socialIdentities()).hasSize(1);
                var stale = login
                        ? original.updateSocialEmail(social, "updated@example.com", true)
                        : original.link(new SocialIdentity(AuthProvider.GOOGLE, "new-link"), clock.instant());
                loaded.countDown();
                await(committed);
                // when / then: 읽었던 영속성 컨텍스트를 유지한 채 탈퇴 커밋 뒤 저장한다.
                assertThatThrownBy(() -> accounts.save(stale))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.UNAUTHORIZED));
                var locked = accounts.findByMemberIdForUpdate(id).orElseThrow();
                assertThat(locked.isWithdrawn()).isTrue();
                assertThat(locked.name()).isNull();
                assertThat(locked.phoneNumber()).isNull();
                assertThat(locked.socialIdentities()).isEmpty();
            }));
            var withdrawal = executor.submit(() -> {
                await(loaded);
                try {
                    return withdrawals.withdraw(id, reauth);
                } finally {
                    // 프록시 호출이 반환된 시점에는 별도 트랜잭션의 커밋이 끝났다.
                    committed.countDown();
                }
            });
            assertThat(withdrawal.get(20, TimeUnit.SECONDS)).isInstanceOf(WithdrawalOutcome.Withdrawn.class);
            staleSave.get(20, TimeUnit.SECONDS);
        }
        assertThat(load(id).status()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(jdbc.sql("SELECT name_enc IS NULL AND phone_enc IS NULL FROM auth_account WHERE member_id = :id")
                        .param("id", id.value())
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(count("auth_social_identity")).isZero();
        assertThat(events.withdrawn).hasSize(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrency test timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test interrupted", exception);
        }
    }

    @Test
    @DisplayName("동시 탈퇴 두 건은 하나만 이벤트를 발행한다")
    void concurrentWithdrawal() throws Exception {
        // given
        register("kakao", "race", PHONE_A);
        var id = member();
        var firstIdentity = reauth("race");
        var secondIdentity = reauth("race");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.function.Function<VerifiedSocialIdentity, java.util.concurrent.Callable<WithdrawalOutcome>> task =
                    identity -> () -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                        return withdrawals.withdraw(id, identity);
                    };
            var first = executor.submit(task.apply(firstIdentity));
            var second = executor.submit(task.apply(secondIdentity));
            // when
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            var outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            // then
            assertThat(outcomes)
                    .filteredOn(o -> o instanceof WithdrawalOutcome.Withdrawn)
                    .hasSize(1);
            assertThat(outcomes)
                    .filteredOn(o -> o instanceof WithdrawalOutcome.AlreadyWithdrawn)
                    .hasSize(1);
            assertThat(events.withdrawn).hasSize(1);
        }
    }

    @Test
    @DisplayName("29일 재가입은 403이고 31일 배치 전 재가입은 새 회원을 만든다")
    void rejoinBeforeCleanup() throws Exception {
        // given
        register("kakao", "old", PHONE_A);
        var old = member();
        withdrawals.withdraw(old, reauth("old"));
        ((MutableAuthClock) clock).advance(Duration.ofDays(29));
        var pending = pending("kakao", "new");
        // when / then
        complete(pending, verification(pending, PHONE_A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_REJOIN_RESTRICTED"));
        ((MutableAuthClock) clock).advance(Duration.ofDays(2));
        events.registered.clear();
        register("kakao", "new", PHONE_A);
        assertThat(load(old).diHash()).isNull();
        assertThat(events.registered).hasSize(1);
        assertThat(events.registered.getFirst().memberId()).isNotEqualTo(old);
        assertThat(count("auth_account")).isEqualTo(2);
    }

    @Test
    @DisplayName("배치는 30일 초과 탈퇴 DI만 지우고 정각과 최근 계정은 남긴다")
    void cleanupBoundary() throws Exception {
        // given
        var old = account("old", AccountStatus.ACTIVE, null);
        withdrawals.withdraw(old, reauth("old"));
        ((MutableAuthClock) clock).advance(Duration.ofNanos(1000));
        var boundary = account("boundary", AccountStatus.ACTIVE, null);
        withdrawals.withdraw(boundary, reauth("boundary"));
        ((MutableAuthClock) clock).advance(Duration.ofDays(30));
        var recent = account("recent", AccountStatus.ACTIVE, null);
        withdrawals.withdraw(recent, reauth("recent"));
        var active = account("active", AccountStatus.ACTIVE, null);
        // when
        cleanup.cleanup();
        // then
        assertThat(load(old).diHash()).isNull();
        assertThat(load(boundary).diHash()).isNotNull();
        assertThat(load(recent).diHash()).isNotNull();
        assertThat(load(active).diHash()).isNotNull();
        int changed = transactions.execute(
                s -> accounts.clearExpiredWithdrawnDiHashes(clock.instant().minus(Duration.ofDays(30))));
        assertThat(changed).isZero();
    }

    @Test
    @DisplayName("탈퇴 뒤 제재는 상태를 유지하며 제재 DI를 등록하고 재가입을 차단한다")
    void sanctionsAfterWithdrawal() throws Exception {
        // given
        register("kakao", "sanction", PHONE_A);
        var id = member();
        withdrawals.withdraw(id, reauth("sanction"));
        // when
        publisher.publishEvent(new MemberSanctioned(id, null));
        // then
        assertThat(load(id).isWithdrawn()).isTrue();
        assertThat(count("auth_sanctioned_di")).isEqualTo(1);
        ((MutableAuthClock) clock).advance(Duration.ofDays(31));
        cleanup.cleanup();
        assertThat(count("auth_sanctioned_di")).isEqualTo(1);
        var pending = pending("kakao", "retry");
        complete(pending, verification(pending, PHONE_A))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_SANCTIONED"));
        publisher.publishEvent(new MemberSanctioned(id, null));
        publisher.publishEvent(new MemberSanctionLifted(id));
        assertThat(load(id).isWithdrawn()).isTrue();
        assertThat(count("auth_sanctioned_di")).isEqualTo(1);
    }

    public static class Events {
        final List<MemberWithdrawn> withdrawn = new CopyOnWriteArrayList<>();
        final List<MemberRegistered> registered = new CopyOnWriteArrayList<>();
        volatile boolean fail;

        @EventListener
        public void withdrawn(MemberWithdrawn event) {
            if (fail) throw new IllegalStateException("Test listener failure");
            withdrawn.add(event);
        }

        @EventListener
        public void registered(MemberRegistered event) {
            registered.add(event);
        }
    }
}
