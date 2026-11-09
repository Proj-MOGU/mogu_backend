package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountInfoServiceTest {
    @Test
    @DisplayName("회원 ID에 해당하는 계정이 없으면 인증 오류로 처리한다")
    void rejectsMissingAccount() {
        // given
        var id = new MemberId(UUID.randomUUID());
        var accounts = mock(AuthAccountRepository.class);
        when(accounts.findByMemberId(id)).thenReturn(Optional.empty());
        // when / then
        assertThatThrownBy(() -> new AccountInfoService(accounts).getAccount(id))
                .isInstanceOfSatisfying(
                        BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.UNAUTHORIZED));
        verify(accounts).findByMemberId(id);
    }
}
