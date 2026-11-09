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

class BioTest {
    @ParameterizedTest
    @ValueSource(strings = {"가", "😀"})
    @DisplayName("소개 길이를 코드 포인트로 세어 오십 자까지 허용한다")
    void checksCodePointLimit(String unit) {
        // given / when / then
        assertThat(Bio.of(unit.repeat(50)).value()).isEqualTo(unit.repeat(50));
        assertThatThrownBy(() -> Bio.of(unit.repeat(51)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.INVALID_BIO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"안\n녕", "안\r녕", "안\t녕", "안\u200B녕", "안\u202E녕", "안\u2028녕", "안\u2029녕", "안\u0000녕"})
    @DisplayName("소개에 포함된 줄바꿈과 제어 형식 문자를 거부한다")
    void rejectsForbiddenCharacters(String value) {
        // given / when / then
        assertThatThrownBy(() -> Bio.of(value))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.INVALID_BIO));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("정규화 후 빈 소개는 없음으로 저장한다")
    void normalizesEmptyBio(String value) {
        // given / when / then
        assertThat(Bio.of(value).value()).isNull();
    }

    @Test
    @DisplayName("소개도 한글 조합과 앞뒤 공백 제거를 적용한다")
    void normalizesBio() {
        // given
        String input = Normalizer.normalize("안녕하세요", Normalizer.Form.NFD);
        // when / then
        assertThat(Bio.of("  " + input + "  ").value()).isEqualTo("안녕하세요");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " 소개 ", "안\n녕", "안\u200B녕", "  한  "})
    @DisplayName("저장된 소개는 null과 원문을 그대로 복원한다")
    void reconstitutesStoredBio(String value) {
        // given / when / then
        assertThat(Bio.reconstitute(value).value()).isEqualTo(value);
    }

    @Test
    @DisplayName("저장된 소개에는 신규 입력 길이 정책을 적용하지 않는다")
    void reconstitutesBioBeyondInputLimit() {
        // given
        String value = "가".repeat(51);
        // when / then
        assertThat(Bio.reconstitute(value).value()).isEqualTo(value);
    }
}
