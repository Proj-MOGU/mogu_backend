package com.mogu.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberProfileTest {
    @Test
    @DisplayName("회원 식별자와 가입 시각으로 미완성 프로필을 생성한다")
    void createsIncompleteProfile() {
        // given
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // when
        MemberProfile profile = MemberProfile.create(memberId, registeredAt);

        // then
        assertThat(profile.memberId()).isEqualTo(memberId);
        assertThat(profile.registeredAt()).isEqualTo(registeredAt);
    }

    @Test
    @DisplayName("회원 식별자가 없으면 생성을 거부한다")
    void rejectsNullMemberId() {
        // given
        Instant registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        // when / then
        assertThatNullPointerException().isThrownBy(() -> MemberProfile.create(null, registeredAt));
    }

    @Test
    @DisplayName("가입 시각이 없으면 생성을 거부한다")
    void rejectsNullRegisteredAt() {
        // given
        MemberId memberId = new MemberId(UUID.randomUUID());

        // when / then
        assertThatNullPointerException().isThrownBy(() -> MemberProfile.create(memberId, null));
    }

    private final Clock clock =
            Clock.fixed(Instant.parse("2026-11-09T07:00:00.123456Z").truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);

    @Test
    @DisplayName("최초 입력은 변경으로 세지 않고 이후 변경부터 주기를 제한한다")
    void countsOnlyChangesAfterCompletion() {
        // given
        MemberProfile profile = MemberProfile.create(new MemberId(UUID.randomUUID()), clock.instant());
        // when
        profile.updateProfile(Nickname.of("별명12"), Bio.of("소개"), clock.instant());
        // then
        assertThat(profile.isCompleted()).isTrue();
        assertThat(profile.nicknameChangedAt()).isNull();
        assertThat(profile.nicknameChangeableAt(clock.instant())).isEmpty();
        // when
        profile.updateProfile(Nickname.of("별명34"), Bio.of(null), clock.instant());
        // then
        assertThat(profile.nicknameChangedAt()).isEqualTo(clock.instant());
        assertThat(profile.nicknameChangeableAt(clock.instant()))
                .contains(clock.instant().plus(Duration.ofDays(30)));
        assertTooSoon(profile, clock.instant());
    }

    @Test
    @DisplayName("삼십일 직전에는 거부하고 정확히 삼십일이 지나면 허용한다")
    void enforcesExactThirtyDayBoundary() {
        // given
        MemberProfile profile = changedProfile();
        Instant boundary = clock.instant().plus(Duration.ofDays(30));
        // when / then
        assertTooSoon(profile, boundary.minusSeconds(1));
        assertThat(profile.nicknameChangeableAt(boundary)).isEmpty();
        profile.updateProfile(Nickname.of("다음별명"), Bio.of(null), boundary);
        assertThat(profile.nicknameChangedAt()).isEqualTo(boundary);
    }

    @Test
    @DisplayName("같은 닉네임은 변경하지 않고 소개만 자유롭게 수정한다")
    void permitsBioChangesAndIdenticalNickname() {
        // given
        MemberProfile profile = changedProfile();
        Instant changedAt = profile.nicknameChangedAt();
        Instant updatedAt = profile.updatedAt();
        // when
        profile.updateProfile(profile.nickname(), profile.bio(), clock.instant().plusSeconds(1));
        // then
        assertThat(profile.updatedAt()).isEqualTo(updatedAt);
        // when
        profile.updateProfile(
                profile.nickname(), Bio.of("새 소개"), clock.instant().plusSeconds(2));
        // then
        assertThat(profile.nicknameChangedAt()).isEqualTo(changedAt);
        assertThat(profile.bio().value()).isEqualTo("새 소개");
        profile.updateProfile(profile.nickname(), Bio.of(null), clock.instant().plusSeconds(3));
        assertThat(profile.bio().value()).isNull();
    }

    @Test
    @DisplayName("대소문자만 바꾸는 것도 변경으로 세며 닉네임을 비울 수 없다")
    void countsCaseChangesAndRejectsClearing() {
        // given
        MemberProfile profile = MemberProfile.create(new MemberId(UUID.randomUUID()), clock.instant());
        profile.updateProfile(Nickname.of("Star12"), Bio.of(null), clock.instant());
        // when
        profile.updateProfile(Nickname.of("STAR12"), Bio.of(null), clock.instant());
        // then
        assertThat(profile.nicknameChangedAt()).isEqualTo(clock.instant());
        assertThatThrownBy(() -> profile.updateProfile(Nickname.of("star12"), Bio.of(null), clock.instant()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.NICKNAME_CHANGE_TOO_SOON));
        assertThatThrownBy(() -> profile.updateProfile(null, Bio.of(null), clock.instant()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.INVALID_NICKNAME));
    }

    private MemberProfile changedProfile() {
        MemberProfile profile = MemberProfile.create(new MemberId(UUID.randomUUID()), clock.instant());
        profile.updateProfile(Nickname.of("처음별명"), Bio.of(null), clock.instant());
        profile.updateProfile(Nickname.of("바꾼별명"), Bio.of(null), clock.instant());
        return profile;
    }

    private void assertTooSoon(MemberProfile profile, Instant now) {
        assertThatThrownBy(() -> profile.updateProfile(Nickname.of("다음별명"), Bio.of(null), now))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.NICKNAME_CHANGE_TOO_SOON));
    }
}
