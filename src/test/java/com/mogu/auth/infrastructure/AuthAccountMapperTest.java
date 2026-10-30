package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.LinkedSocialIdentity;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthAccountMapperTest {
    private final AuthAccountMapper mapper = new AuthAccountMapper();
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-27T00:00:00Z");

    @Test
    @DisplayName("계정 엔티티의 모든 필드와 연결 목록을 도메인으로 복원한다")
    void toDomainMapsEveryField() {
        // given
        for (var status : AccountStatus.values()) {
            var until = status == AccountStatus.ACTIVE ? null : now.plusSeconds(100);
            var entity = new AuthAccountJpaEntity(
                    id,
                    "a".repeat(64),
                    status,
                    until,
                    now,
                    List.of(new AuthSocialIdentityJpaEntity(AuthProvider.GOOGLE, "sub", id, now.plusSeconds(1))));
            // when
            var account = mapper.toDomain(entity);
            // then
            assertThat(account.memberId()).isEqualTo(new MemberId(id));
            assertThat(account.diHash().value()).isEqualTo("a".repeat(64));
            assertThat(account.status()).isEqualTo(status);
            assertThat(account.sanctionedUntil()).isEqualTo(until);
            assertThat(account.createdAt()).isEqualTo(now);
            assertThat(account.socialIdentities())
                    .containsExactly(new LinkedSocialIdentity(
                            new SocialIdentity(AuthProvider.GOOGLE, "sub"), now.plusSeconds(1)));
        }
    }

    @Test
    @DisplayName("계정 도메인의 모든 필드와 연결 목록을 엔티티로 변환한다")
    void toJpaEntityMapsEveryField() {
        // given
        for (var status : AccountStatus.values()) {
            var until = status == AccountStatus.ACTIVE ? null : now.plusSeconds(100);
            var account = AuthAccount.reconstitute(
                    new MemberId(id),
                    new com.mogu.auth.domain.DiHash("a".repeat(64)),
                    status,
                    until,
                    now,
                    List.of(new LinkedSocialIdentity(
                            new SocialIdentity(AuthProvider.KAKAO, "sub"), now.plusSeconds(1))));
            // when
            var entity = mapper.toJpaEntity(account);
            // then
            assertThat(entity.getMemberId()).isEqualTo(id);
            assertThat(entity.getDiHash()).isEqualTo("a".repeat(64));
            assertThat(entity.getStatus()).isEqualTo(status);
            assertThat(entity.getSanctionedUntil()).isEqualTo(until);
            assertThat(entity.getCreatedAt()).isEqualTo(now);
            assertThat(entity.getSocialIdentities()).hasSize(1);
            var linked = entity.getSocialIdentities().getFirst();
            assertThat(linked.getMemberId()).isEqualTo(id);
            assertThat(linked.getProvider()).isEqualTo(AuthProvider.KAKAO);
            assertThat(linked.getProviderSubject()).isEqualTo("sub");
            assertThat(linked.getLinkedAt()).isEqualTo(now.plusSeconds(1));
        }
    }
}
