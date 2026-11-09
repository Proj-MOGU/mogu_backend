package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PiiMaskingTest {
    @ParameterizedTest
    @CsvSource({"01012345678,010-****-5678", "0111234567,011-***-4567"})
    @DisplayName("10자리와 11자리 번호를 규칙대로 가린다")
    void masksPhone(String input, String expected) {
        // given / when / then
        assertThat(PiiMasking.phoneNumber(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"hong@gmail.com,ho***@gmail.com", "abc@b.com,ab***@b.com", "ab@b.com,a***@b.com", "a@b.com,a***@b.com"})
    @DisplayName("이메일 로컬 부분 1·2·3자 경계와 도메인을 보존한다")
    void masksEmail(String input, String expected) {
        // given / when / then
        assertThat(PiiMasking.email(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123456789", "123456789012", "abcdefghij", "010-1234-5678"})
    @DisplayName("유효하지 않은 번호 원문을 반환하지 않는다")
    void omitsInvalidPhone(String input) {
        // given / when / then
        assertThat(PiiMasking.phoneNumber(input)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"no-at", "@b.com", "a@"})
    @DisplayName("유효하지 않은 이메일 원문을 반환하지 않는다")
    void omitsInvalidEmail(String input) {
        // given / when / then
        assertThat(PiiMasking.email(input)).isNull();
    }
}
