package com.mogu.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthProviderTest {
    @Test
    @DisplayName("공급자 이름은 대소문자를 구분하지 않는다")
    void parsesProvider() {
        // given / when / then
        assertThat(AuthProvider.from("kakao")).isEqualTo(AuthProvider.KAKAO);
        assertThat(AuthProvider.from("GOOGLE")).isEqualTo(AuthProvider.GOOGLE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"apple", ""})
    @DisplayName("알 수 없는 공급자를 거부한다")
    void rejectsUnsupported(String value) {
        // given / when / then
        assertThatThrownBy(() -> AuthProvider.from(value))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.UNSUPPORTED_PROVIDER));
    }
}
