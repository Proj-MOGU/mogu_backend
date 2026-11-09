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
        return AuthAccount.reconstitute(
                new MemberId(UUID.randomUUID()),
                new com.mogu.auth.domain.DiHash("a".repeat(64)),
                status,
                until,
                now,
                List.of());
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
        var account = AuthAccount.reconstitute(
                new MemberId(UUID.randomUUID()),
                new com.mogu.auth.domain.DiHash("a".repeat(64)),
                AccountStatus.ACTIVE,
                null,
                now,
                identities);
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

    @Test
    @DisplayName("가입과 연결은 기존 상태를 보존하고 중복 공급자를 거부한다")
    void registersAndLinks() {
        // given
        var id = new MemberId(UUID.randomUUID());
        var hash = new DiHash("b".repeat(64));
        var kakao = new SocialIdentity(AuthProvider.KAKAO, "first");
        // when
        var account = AuthAccount.register(id, hash, kakao, now);
        var linked = account.link(new SocialIdentity(AuthProvider.GOOGLE, "second"), now.plusSeconds(1));
        // then
        assertThat(account.memberId()).isEqualTo(id);
        assertThat(account.diHash()).isEqualTo(hash);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.createdAt()).isEqualTo(now);
        assertThat(account.socialIdentities()).containsExactly(new LinkedSocialIdentity(kakao, now));
        assertThat(account.link(kakao, now.plusSeconds(1))).isSameAs(account);
        assertThat(linked.socialIdentities()).hasSize(2);
        assertThat(linked.memberId()).isEqualTo(id);
        assertThat(linked.diHash()).isEqualTo(hash);
        assertThatThrownBy(() -> account.link(new SocialIdentity(AuthProvider.KAKAO, "other"), now))
                .isInstanceOfSatisfying(
                        com.mogu.common.error.BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.PROVIDER_ALREADY_LINKED));
    }

    @Test
    @DisplayName("제재와 해제는 새 객체로 상태와 기한을 변경한다")
    void sanctionsAndLifts() {
        // given
        var original = account(AccountStatus.ACTIVE, null);
        // when
        var sanctioned = original.sanction(now.plusSeconds(1));
        var lifted = sanctioned.lift();
        // then
        assertThat(original.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(sanctioned.status()).isEqualTo(AccountStatus.SANCTIONED);
        assertThat(sanctioned.sanctionedUntil()).isEqualTo(now.plusSeconds(1));
        assertThat(sanctioned.isSanctionedAt(now)).isTrue();
        assertThat(original.sanction(null).isSanctionedAt(now)).isTrue();
        assertThat(lifted.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(lifted.sanctionedUntil()).isNull();
        assertThat(lifted.diHash()).isEqualTo(original.diHash());
        assertThat(lifted.memberId()).isEqualTo(original.memberId());
    }
}
