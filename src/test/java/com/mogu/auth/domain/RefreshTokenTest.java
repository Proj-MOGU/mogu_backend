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
}
