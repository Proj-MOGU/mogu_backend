package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.support.PiiTestKeys;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthAccountRepositoryPiiTest {
    @Test
    @DisplayName("복호화하지 못한 기존 암호문은 다른 상태 저장 때 삭제하지 않는다")
    void preservesUnreadableCiphertext() {
        // given
        var accounts = mock(JpaAuthAccountRepository.class);
        var identities = mock(JpaAuthSocialIdentityRepository.class);
        var mapper = new AuthAccountMapper(PiiTestKeys.cipher());
        var id = UUID.randomUUID();
        var now = Instant.now();
        var linked = new AuthSocialIdentityJpaEntity(AuthProvider.KAKAO, "subject", id, now, "v99.lost.email", true);
        var entity = new AuthAccountJpaEntity(
                id,
                "a".repeat(64),
                AccountStatus.ACTIVE,
                null,
                now,
                List.of(linked),
                "v99.lost.name",
                "v99.lost.phone");
        when(accounts.findById(id)).thenReturn(Optional.of(entity));
        when(identities.findById(any())).thenReturn(Optional.of(linked));
        // when
        new AuthAccountRepositoryAdapter(accounts, identities, mapper)
                .save(mapper.toDomain(entity).sanction(null));
        // then
        var saved = ArgumentCaptor.forClass(AuthAccountJpaEntity.class);
        verify(accounts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getNameEnc()).isEqualTo("v99.lost.name");
        assertThat(saved.getValue().getPhoneEnc()).isEqualTo("v99.lost.phone");
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.SANCTIONED);
        verify(identities).saveAllAndFlush(org.mockito.ArgumentMatchers.argThat(values -> {
            var value = values.iterator().next();
            return value.getEmailEnc().equals("v99.lost.email") && value.isEmailVerified();
        }));
    }
}
