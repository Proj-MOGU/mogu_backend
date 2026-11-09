package com.mogu.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.auth.MemberId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {
    @Test
    @DisplayName("발급 시 식별자와 해시를 보존하고 만료를 계산한다")
    void issuesWithExpiry() {
        // given
        var id = UUID.randomUUID();
        var family = UUID.randomUUID();
        var member = new MemberId(UUID.randomUUID());
        var now = Instant.parse("2026-10-27T00:00:00Z");
        // when
        var token = RefreshToken.issue(id, family, member, "a".repeat(64), now, Duration.ofDays(30));
        // then
        assertThat(token.id()).isEqualTo(id);
        assertThat(token.familyId()).isEqualTo(family);
        assertThat(token.memberId()).isEqualTo(member);
        assertThat(token.tokenHash()).isEqualTo("a".repeat(64));
        assertThat(token.issuedAt()).isEqualTo(now);
        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
        assertThat(token.usedAt()).isNull();
        assertThat(token.revokedAt()).isNull();
        assertThatThrownBy(() -> RefreshToken.issue(id, family, member, "a".repeat(64), now, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("만료 정각부터 만료이며 사용과 폐기 상태를 각각 판정한다")
    void detectsStateAndExactExpiry() {
        // given
        var now = Instant.parse("2026-11-01T00:00:00Z");
        var active = RefreshToken.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new MemberId(UUID.randomUUID()),
                "a".repeat(64),
                now,
                Duration.ofDays(30));
        // when
        var used = RefreshToken.reconstitute(
                active.id(),
                active.familyId(),
                active.memberId(),
                active.tokenHash(),
                active.issuedAt(),
                active.expiresAt(),
                now,
                null);
        var revoked = RefreshToken.reconstitute(
                active.id(),
                active.familyId(),
                active.memberId(),
                active.tokenHash(),
                active.issuedAt(),
                active.expiresAt(),
                null,
                now);
        // then
        assertThat(active.isUsed()).isFalse();
        assertThat(active.isRevoked()).isFalse();
        assertThat(active.isExpiredAt(active.expiresAt().minusNanos(1))).isFalse();
        assertThat(active.isExpiredAt(active.expiresAt())).isTrue();
        assertThat(active.isExpiredAt(active.expiresAt().plusNanos(1))).isTrue();
        assertThat(used.isUsed()).isTrue();
        assertThat(used.isRevoked()).isFalse();
        assertThat(revoked.isUsed()).isFalse();
        assertThat(revoked.isRevoked()).isTrue();
    }
}
