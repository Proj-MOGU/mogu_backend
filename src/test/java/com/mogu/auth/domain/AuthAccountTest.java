package com.mogu.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthAccountTest {
    private final Instant now = Instant.parse("2026-10-27T00:00:00Z");

    private AuthAccount account(AccountStatus status, Instant until) {
        return AuthAccount.reconstitute(new MemberId(UUID.randomUUID()), status, until, now, List.of());
    }

    @Test
    @DisplayName("제재 기한 직전과 정각 및 무기한을 판정한다")
    void sanctionBoundaries() {
        // given
        var temporary = account(AccountStatus.SANCTIONED, now);
        // when / then
        assertThat(temporary.isSanctionedAt(now.minusNanos(1))).isTrue();
        assertThat(temporary.isSanctionedAt(now)).isFalse();
        assertThat(temporary.isSanctionedAt(now.plusNanos(1))).isFalse();
        assertThat(account(AccountStatus.SANCTIONED, null).isSanctionedAt(now)).isTrue();
        assertThat(account(AccountStatus.ACTIVE, null).isSanctionedAt(now)).isFalse();
    }

    @Test
    @DisplayName("소셜 연결 컬렉션은 입력과 출력 모두 변경으로부터 보호한다")
    void copiesIdentities() {
        // given
        var linked = new LinkedSocialIdentity(new SocialIdentity(AuthProvider.KAKAO, "subject"), now);
        var identities = new ArrayList<>(List.of(linked));
        // when
        var account =
                AuthAccount.reconstitute(new MemberId(UUID.randomUUID()), AccountStatus.ACTIVE, null, now, identities);
        identities.clear();
        // then
        assertThat(account.socialIdentities()).containsExactly(linked);
        assertThatThrownBy(() -> account.socialIdentities().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("소셜 식별자의 필수값과 최대 길이를 검증한다")
    void validatesSocialIdentity() {
        // given / when / then
        assertThatThrownBy(() -> new SocialIdentity(null, "s")).isInstanceOf(NullPointerException.class);
        for (String value : new String[] {null, "", " ", "a".repeat(256)})
            assertThatThrownBy(() -> new SocialIdentity(AuthProvider.GOOGLE, value))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SocialIdentity(AuthProvider.GOOGLE, "a".repeat(255)).subject())
                .hasSize(255);
    }
}
