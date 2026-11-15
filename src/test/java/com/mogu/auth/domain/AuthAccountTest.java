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

    @Test
    @DisplayName("탈퇴는 개인정보와 소셜 연결을 삭제하고 DI와 마이크로초 시각을 보존한다")
    void withdrawalClearsPersonalData() {
        // given
        var clock = new com.mogu.auth.support.MutableAuthClock();
        var social = new SocialIdentity(AuthProvider.KAKAO, "withdraw");
        var original = AuthAccount.register(new MemberId(UUID.randomUUID()), new DiHash("a".repeat(64)), social, now)
                .updatePersonalInfo("테스트", "01012345678")
                .updateSocialEmail(social, "test@example.com", true);
        // when
        var withdrawn = original.withdraw(clock.instant());
        // then
        assertThat(withdrawn.isWithdrawn()).isTrue();
        assertThat(withdrawn.withdrawnAt()).isEqualTo(clock.instant());
        assertThat(withdrawn.name()).isNull();
        assertThat(withdrawn.phoneNumber()).isNull();
        assertThat(withdrawn.socialIdentities()).isEmpty();
        assertThat(withdrawn.diHash()).isEqualTo(original.diHash());
        assertThat(withdrawn.clearDiHash().diHash()).isNull();
        assertThat(original.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 계정은 모든 변경과 재탈퇴를 거부한다")
    void withdrawnRejectsChanges() {
        // given
        var withdrawn = account(AccountStatus.ACTIVE, null).withdraw(now);
        var social = new SocialIdentity(AuthProvider.KAKAO, "withdraw");
        // when / then
        for (Runnable change : List.<Runnable>of(
                () -> withdrawn.link(social, now),
                () -> withdrawn.sanction(null),
                () -> withdrawn.lift(),
                () -> withdrawn.updatePersonalInfo(null, null),
                () -> withdrawn.updateSocialEmail(social, null, false),
                () -> withdrawn.withdraw(now)))
            assertThatThrownBy(change::run).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> account(AccountStatus.ACTIVE, null).clearDiHash())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("제재 중 탈퇴는 거절하고 제재 만료 정각부터 허용한다")
    void withdrawalRespectsSanctions() {
        // given / when / then
        for (Instant until : new Instant[] {null, now.plusSeconds(1)})
            assertThatThrownBy(() -> account(AccountStatus.SANCTIONED, until).withdraw(now))
                    .isInstanceOfSatisfying(
                            com.mogu.common.error.BusinessException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.ACCOUNT_SANCTIONED));
        assertThat(account(AccountStatus.SANCTIONED, now).withdraw(now).isWithdrawn())
                .isTrue();
    }

    @Test
    @DisplayName("탈퇴 시각과 DI의 생성자 불변식을 검사한다")
    void withdrawalInvariants() {
        // given
        var id = new MemberId(UUID.randomUUID());
        // when / then
        assertThatThrownBy(() -> AuthAccount.reconstitute(
                        id, new DiHash("a".repeat(64)), AccountStatus.ACTIVE, null, now, List.of(), null, null, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthAccount.reconstitute(id, null, AccountStatus.ACTIVE, null, now, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuthAccount.reconstitute(id, null, AccountStatus.WITHDRAWN, null, now, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재가입 제한은 정확히 30일 정각에 끝난다")
    void rejoinBoundary() {
        // given
        var restriction = java.time.Duration.ofDays(30);
        var withdrawn = account(AccountStatus.ACTIVE, null).withdraw(now);
        // when / then
        assertThat(withdrawn.isRejoinRestrictedAt(now.plus(restriction).minusNanos(1000), restriction))
                .isTrue();
        assertThat(withdrawn.isRejoinRestrictedAt(now.plus(restriction), restriction))
                .isFalse();
        assertThat(withdrawn.isRejoinRestrictedAt(now.plus(restriction).plusNanos(1000), restriction))
                .isFalse();
        assertThat(account(AccountStatus.ACTIVE, null).isRejoinRestrictedAt(now, restriction))
                .isFalse();
    }
}
