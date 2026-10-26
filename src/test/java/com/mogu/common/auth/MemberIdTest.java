package com.mogu.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberIdTest {
    @Test
    @DisplayName("정상 UUID 문자열을 회원 식별자로 변환한다")
    void validUuidIsParsed() {
        // given
        var value = "00000000-0000-7000-8000-000000000001";
        // when
        var member = MemberId.from(value);
        // then
        assertThat(member.value()).isEqualTo(UUID.fromString(value));
        assertThat(member.toString()).isEqualTo(value);
    }

    @Test
    @DisplayName("잘못된 문자열과 축약 UUID를 거부한다")
    void invalidUuidIsRejected() {
        // given / when / then
        assertThatThrownBy(() -> MemberId.from("1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberId.from("1-1-1-1-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("문자열과 생성자에서 null을 거부한다")
    void nullIsRejected() {
        // given / when / then
        assertThatThrownBy(() -> MemberId.from(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemberId(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
