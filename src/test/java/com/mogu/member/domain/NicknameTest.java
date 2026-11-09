package com.mogu.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.error.BusinessException;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NicknameTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"가", "abcdefghijklm", "두 글자", "별명!", "ㅋㅋ", "a\nb", "a\tb", "a\u200Bb", "a\u202Eb", "   "})
    @DisplayName("길이와 허용 문자 규칙을 벗어나는 닉네임을 거부한다")
    void rejectsInvalidNickname(String value) {
        // given / when / then
        assertThatThrownBy(() -> Nickname.of(value))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.INVALID_NICKNAME));
    }

    @ParameterizedTest
    @ValueSource(strings = {"가나", "abcdefghijkl", "별명12", "AbC123"})
    @DisplayName("경계 길이와 한글 영문 숫자를 허용한다")
    void acceptsValidNickname(String value) {
        // given / when
        Nickname nickname = Nickname.of(value);
        // then
        assertThat(nickname.value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"운영자123", "ADMIN", "관리자님", "운영팀원", "고객센터1", "xxOfficial"})
    @DisplayName("예약어가 포함된 닉네임을 거부한다")
    void rejectsReservedNickname(String value) {
        // given / when / then
        assertThatThrownBy(() -> Nickname.of(value))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.RESERVED_NICKNAME));
    }

    @ParameterizedTest
    @ValueSource(strings = {"모구", "MOGU", "모구짱", "mogu123", "모구mogu", "MoguFan"})
    @DisplayName("서비스 이름이 포함된 닉네임을 허용한다")
    void acceptsNicknameContainingServiceName(String value) {
        // given / when
        Nickname nickname = Nickname.of(value);
        // then
        assertThat(nickname.value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"모구운영자", "moguAdmin"})
    @DisplayName("서비스 이름과 운영진 사칭 예약어가 함께 포함된 닉네임을 거부한다")
    void rejectsServiceNameWithReservedWord(String value) {
        // given / when / then
        assertThatThrownBy(() -> Nickname.of(value))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.RESERVED_NICKNAME));
    }

    @Test
    @DisplayName("분리된 한글을 조합하고 앞뒤 공백과 대소문자 비교 키를 정규화한다")
    void normalizesNickname() {
        // given
        String value = Normalizer.normalize("한글Ab", Normalizer.Form.NFD);
        // when
        Nickname nickname = Nickname.of("  " + value + "  ");
        // then
        assertThat(nickname.value()).isEqualTo("한글Ab");
        assertThat(nickname.key()).isEqualTo(Nickname.of("한글ab").key());
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin1", "OFFICIAL", "", "a", "abcdefghijklm", " 별명! ", "  한Ab  "})
    @DisplayName("저장된 닉네임은 정규화나 입력 정책 검사 없이 복원한다")
    void reconstitutesStoredNickname(String value) {
        // given / when
        Nickname nickname = Nickname.reconstitute(value);
        // then
        assertThat(nickname.value()).isEqualTo(value);
        assertThat(nickname.key()).isEqualTo(value.toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    @DisplayName("저장된 닉네임도 null은 거부한다")
    void rejectsNullStoredNickname() {
        // given / when / then
        assertThatThrownBy(() -> Nickname.reconstitute(null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.INVALID_NICKNAME));
    }
}
