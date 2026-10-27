package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.domain.RefreshToken;
import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenMapperTest {
    private final RefreshTokenMapper mapper = new RefreshTokenMapper();
    private final UUID id = UUID.randomUUID(), family = UUID.randomUUID(), member = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-27T00:00:00Z");

    @Test
    @DisplayName("Refresh Token의 모든 필드와 nullable 값을 도메인으로 복원한다")
    void toDomainMapsEveryField() {
        // given
        for (boolean populated : new boolean[] {false, true}) {
            var used = populated ? now.plusSeconds(10) : null;
            var revoked = populated ? now.plusSeconds(20) : null;
            var source = new RefreshTokenJpaEntity(
                    id, family, member, "a".repeat(64), now, now.plusSeconds(300), used, revoked);
            // when
            var result = mapper.toDomain(source);
            // then
            assertThat(result.id()).isEqualTo(id);
            assertThat(result.familyId()).isEqualTo(family);
            assertThat(result.memberId()).isEqualTo(new MemberId(member));
            assertThat(result.tokenHash()).isEqualTo("a".repeat(64));
            assertThat(result.issuedAt()).isEqualTo(now);
            assertThat(result.expiresAt()).isEqualTo(now.plusSeconds(300));
            assertThat(result.usedAt()).isEqualTo(used);
            assertThat(result.revokedAt()).isEqualTo(revoked);
        }
    }

    @Test
    @DisplayName("Refresh Token의 모든 필드와 nullable 값을 엔티티로 변환한다")
    void toJpaEntityMapsEveryField() {
        // given
        for (boolean populated : new boolean[] {false, true}) {
            var used = populated ? now.plusSeconds(10) : null;
            var revoked = populated ? now.plusSeconds(20) : null;
            var source = RefreshToken.reconstitute(
                    id, family, new MemberId(member), "a".repeat(64), now, now.plusSeconds(300), used, revoked);
            // when
            var result = mapper.toJpaEntity(source);
            // then
            assertThat(result.getId()).isEqualTo(id);
            assertThat(result.getFamilyId()).isEqualTo(family);
            assertThat(result.getMemberId()).isEqualTo(member);
            assertThat(result.getTokenHash()).isEqualTo("a".repeat(64));
            assertThat(result.getIssuedAt()).isEqualTo(now);
            assertThat(result.getExpiresAt()).isEqualTo(now.plusSeconds(300));
            assertThat(result.getUsedAt()).isEqualTo(used);
            assertThat(result.getRevokedAt()).isEqualTo(revoked);
        }
    }
}
