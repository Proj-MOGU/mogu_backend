package com.mogu.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
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
}
