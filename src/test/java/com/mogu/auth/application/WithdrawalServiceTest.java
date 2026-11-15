package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mogu.auth.domain.*;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WithdrawalServiceTest {
    private final WithdrawalAccountQuery accounts = mock(WithdrawalAccountQuery.class);
    private final SocialTokenVerificationService verification = mock(SocialTokenVerificationService.class);
    private final WithdrawalCompletionService completion = mock(WithdrawalCompletionService.class);
    private final WithdrawalService service = new WithdrawalService(accounts, verification, completion);
    private final MemberId id = new MemberId(UUID.randomUUID());
    private final VerifiedSocialIdentity verified = new VerifiedSocialIdentity(AuthProvider.KAKAO, "mine", "nonce");

    @Test
    @DisplayName("계정 조회 후 검증과 탈퇴를 순서대로 호출하며 두 정상 결과를 받아들인다")
    void completesBothOutcomes() {
        // given
        when(verification.verifyToken(AuthProvider.KAKAO, "token")).thenReturn(verified);
        when(completion.withdraw(id, verified))
                .thenReturn(new WithdrawalOutcome.Withdrawn(), new WithdrawalOutcome.AlreadyWithdrawn());
        // when / then
        for (int i = 0; i < 2; i++) {
            service.withdraw(id, AuthProvider.KAKAO, "token");
        }
        var order = inOrder(accounts, verification, completion);
        for (int i = 0; i < 2; i++) {
            order.verify(accounts).isWithdrawn(id);
            order.verify(verification).verifyToken(AuthProvider.KAKAO, "token");
            order.verify(completion).withdraw(id, verified);
        }
    }

    @Test
    @DisplayName("이미 탈퇴했거나 계정이 없으면 공급자 검증과 탈퇴 트랜잭션을 호출하지 않는다")
    void shortCircuitsBeforeVerification() {
        // given
        when(accounts.isWithdrawn(id)).thenReturn(true);
        // when
        service.withdraw(id, AuthProvider.KAKAO, "invalid");
        // then
        verifyNoInteractions(verification, completion);
        when(accounts.isWithdrawn(id)).thenThrow(new BusinessException(AuthErrorCode.UNAUTHORIZED));
        assertError(AuthErrorCode.UNAUTHORIZED);
        verifyNoInteractions(verification, completion);
    }

    @ParameterizedTest
    @EnumSource(
            value = AuthErrorCode.class,
            names = {
                "INVALID_SOCIAL_TOKEN",
                "UNSUPPORTED_PROVIDER",
                "PROVIDER_UNAVAILABLE",
                "ACCOUNT_SANCTIONED",
                "REAUTHENTICATION_FAILED",
                "UNAUTHORIZED"
            })
    @DisplayName("검증과 트랜잭션 양쪽의 소셜 토큰 오류만 재인증 실패 403으로 바꾼다")
    void translatesOnlySocialTokenErrors(AuthErrorCode error) {
        // given
        var failure = new BusinessException(error);
        var expected = error == AuthErrorCode.INVALID_SOCIAL_TOKEN ? AuthErrorCode.REAUTHENTICATION_FAILED : error;
        when(verification.verifyToken(AuthProvider.KAKAO, "token")).thenThrow(failure);
        // when / then
        assertError(expected);
        verifyNoInteractions(completion);
        reset(verification);
        when(verification.verifyToken(AuthProvider.KAKAO, "token")).thenReturn(verified);
        when(completion.withdraw(id, verified)).thenThrow(failure);
        assertError(expected);
    }

    @ParameterizedTest
    @EnumSource(
            value = AuthErrorCode.class,
            names = {"INVALID_SOCIAL_TOKEN", "REAUTHENTICATION_FAILED"})
    @DisplayName("재인증 실패 뒤 재조회에서 이미 탈퇴했으면 정상 종료한다")
    void reauthenticationFailureIsIdempotentAfterConcurrentWithdrawal(AuthErrorCode error) {
        // given
        when(accounts.isWithdrawn(id)).thenReturn(false, true);
        when(verification.verifyToken(AuthProvider.KAKAO, "token")).thenReturn(verified);
        when(completion.withdraw(id, verified)).thenThrow(new BusinessException(error));
        // when / then
        assertThatCode(() -> service.withdraw(id, AuthProvider.KAKAO, "token")).doesNotThrowAnyException();
        var order = inOrder(accounts, completion);
        order.verify(accounts).isWithdrawn(id);
        order.verify(completion).withdraw(id, verified);
        order.verify(accounts).isWithdrawn(id);
        verify(accounts, times(2)).isWithdrawn(id);
    }

    @ParameterizedTest
    @EnumSource(
            value = AuthErrorCode.class,
            names = {"INVALID_SOCIAL_TOKEN", "REAUTHENTICATION_FAILED"})
    @DisplayName("재인증 실패 뒤에도 탈퇴하지 않았으면 403을 유지한다")
    void reauthenticationFailureRemainsForbiddenForActiveAccount(AuthErrorCode error) {
        // given
        when(accounts.isWithdrawn(id)).thenReturn(false);
        when(verification.verifyToken(AuthProvider.KAKAO, "token")).thenReturn(verified);
        when(completion.withdraw(id, verified)).thenThrow(new BusinessException(error));
        // when / then
        assertError(AuthErrorCode.REAUTHENTICATION_FAILED);
        verify(accounts, times(2)).isWithdrawn(id);
    }

    @ParameterizedTest
    @EnumSource(
            value = AuthErrorCode.class,
            names = {"UNSUPPORTED_PROVIDER", "PROVIDER_UNAVAILABLE", "ACCOUNT_SANCTIONED", "UNAUTHORIZED"})
    @DisplayName("입력과 공급자 장애 및 제재 오류에는 탈퇴 상태를 재확인하지 않는다")
    void otherErrorsDoNotRecheckWithdrawal(AuthErrorCode error) {
        // given
        when(accounts.isWithdrawn(id)).thenReturn(false, true);
        when(verification.verifyToken(AuthProvider.KAKAO, "token")).thenReturn(verified);
        var failure = new BusinessException(error);
        when(completion.withdraw(id, verified)).thenThrow(failure);
        // when / then
        assertThatThrownBy(() -> service.withdraw(id, AuthProvider.KAKAO, "token"))
                .isSameAs(failure);
        verify(accounts).isWithdrawn(id);
    }

    private void assertError(AuthErrorCode error) {
        assertThatThrownBy(() -> service.withdraw(id, AuthProvider.KAKAO, "token"))
                .isInstanceOfSatisfying(
                        BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(error));
    }
}
