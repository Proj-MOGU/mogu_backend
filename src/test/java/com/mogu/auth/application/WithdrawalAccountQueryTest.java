package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mogu.auth.domain.*;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WithdrawalAccountQueryTest {
    @Test
    @DisplayName("잠금 없는 조회로 탈퇴 여부를 반환하고 없는 계정은 401로 거절한다")
    void readsAccountStatus() {
        // given
        var accounts = mock(AuthAccountRepository.class);
        var query = new WithdrawalAccountQuery(accounts);
        var id = new MemberId(UUID.randomUUID());
        var account = AuthAccount.register(
                id, new DiHash("a".repeat(64)), new SocialIdentity(AuthProvider.KAKAO, "mine"), Instant.now());
        when(accounts.findByMemberId(id))
                .thenReturn(Optional.of(account), Optional.of(account.withdraw(Instant.now())), Optional.empty());
        // when / then
        assertThat(query.isWithdrawn(id)).isFalse();
        assertThat(query.isWithdrawn(id)).isTrue();
        assertThatThrownBy(() -> query.isWithdrawn(id))
                .isInstanceOfSatisfying(
                        BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.UNAUTHORIZED));
        verify(accounts, never()).findByMemberIdForUpdate(any());
        verify(accounts, never()).save(any());
    }
}
