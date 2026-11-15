package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mogu.auth.domain.*;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(WithdrawalTransactionBoundaryTest.Config.class)
class WithdrawalTransactionBoundaryTest {
    @Autowired
    private WithdrawalService service;

    @Autowired
    private AuthAccountRepository accounts;

    @Autowired
    private VerificationStub stub;

    private SocialTokenVerificationService verification;

    @Autowired
    private RecordingTransactionManager transactions;

    private final MemberId id = new MemberId(UUID.randomUUID());
    private final VerifiedSocialIdentity identity = new VerifiedSocialIdentity(AuthProvider.KAKAO, "mine", "nonce");

    @BeforeEach
    void prepare() {
        verification = stub.value();
        reset(accounts, verification);
        transactions.committed = 0;
        transactions.rolledBack = 0;
        var account = AuthAccount.register(
                id,
                new DiHash("a".repeat(64)),
                new SocialIdentity(AuthProvider.KAKAO, "mine"),
                java.time.Instant.now());
        when(accounts.findByMemberId(id)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                    .isTrue();
            return Optional.of(account);
        });
        when(verification.verifyToken(AuthProvider.KAKAO, "token")).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            return identity;
        });
        when(accounts.findByMemberIdForUpdate(id)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                    .isFalse();
            return Optional.of(account);
        });
    }

    @Test
    @DisplayName("조회 트랜잭션을 닫고 공급자를 검증한 뒤 별도 쓰기 트랜잭션으로 탈퇴한다")
    void verifiesOutsideDatabaseTransactions() {
        // given / when
        service.withdraw(id, AuthProvider.KAKAO, "token");
        // then
        assertThat(transactions.committed).isEqualTo(2);
        assertThat(transactions.rolledBack).isZero();
        verify(accounts).save(argThat(AuthAccount::isWithdrawn));
    }

    @Test
    @DisplayName("nonce 오류는 탈퇴 트랜잭션 롤백이 끝난 후 퍼사드에서 403으로 변환한다")
    void translatesAfterRollback() {
        // given
        doAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .isTrue();
                    throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
                })
                .when(verification)
                .consumeNonce(identity);
        // when / then
        assertThatThrownBy(() -> service.withdraw(id, AuthProvider.KAKAO, "token"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.REAUTHENTICATION_FAILED));
        assertThat(transactions.committed).isEqualTo(2);
        assertThat(transactions.rolledBack).isEqualTo(1);
        verify(accounts, times(2)).findByMemberId(id);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isFalse();
        verify(accounts, never()).save(any());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean
        AuthAccountRepository accounts() {
            return mock(AuthAccountRepository.class);
        }

        @Bean
        VerificationStub stub() {
            return new VerificationStub(mock(SocialTokenVerificationService.class));
        }

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        WithdrawalAccountQuery query(AuthAccountRepository accounts) {
            return new WithdrawalAccountQuery(accounts);
        }

        @Bean
        WithdrawalCompletionService completion(AuthAccountRepository accounts, VerificationStub stub) {
            return new WithdrawalCompletionService(
                    accounts,
                    mock(RefreshTokenRepository.class),
                    stub.value(),
                    mock(ApplicationEventPublisher.class),
                    Clock.systemUTC());
        }

        @Bean
        WithdrawalService service(
                WithdrawalAccountQuery query, VerificationStub stub, WithdrawalCompletionService completion) {
            return new WithdrawalService(query, stub.value(), completion);
        }
    }

    record VerificationStub(SocialTokenVerificationService value) {}

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int committed;
        int rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack++;
        }
    }
}
