package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.auth.support.PiiTestKeys;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AesGcmPiiCipherTest {
    private final AesGcmPiiCipher cipher = PiiTestKeys.cipher();

    @Test
    @DisplayName("한글 개인정보가 왕복되고 같은 평문도 매번 다른 IV를 사용한다")
    void roundTripUsesRandomIv() {
        // given
        String value = "테스트사용자";
        // when
        String first = cipher.encrypt(value, "auth_account.name_enc:member");
        String second = cipher.encrypt(value, "auth_account.name_enc:member");
        // then
        assertThat(first).startsWith("v1.").doesNotContain("=").isNotEqualTo(second);
        assertThat(cipher.decrypt(first, "auth_account.name_enc:member")).isEqualTo(value);
    }

    @Test
    @DisplayName("다른 소유자나 필드의 AAD로 복호화할 수 없다")
    void rejectsDifferentContext() {
        // given
        String encrypted = cipher.encrypt("private", "auth_account.name_enc:a");
        // when / then
        assertThatThrownBy(() -> cipher.decrypt(encrypted, "auth_account.name_enc:b"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(encrypted, "auth_account.phone_enc:a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("암호문 한 바이트 변조를 거부하고 예외에 값을 넣지 않는다")
    void rejectsTampering() {
        // given
        String[] parts = cipher.encrypt("private", "context").split("\\.");
        byte[] bytes = Base64.getUrlDecoder().decode(parts[2]);
        bytes[0] ^= 1;
        String tampered = parts[0] + "." + parts[1] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // when / then
        assertThatThrownBy(() -> cipher.decrypt(tampered, "context"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PII cipher operation failed")
                .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "broken", "v1.a.b", "v1.a.b.c", "v1.****************.abc", "v1.AAAAAAAAAAAAAAAA.AA=="})
    @DisplayName("깨진 저장 형식을 거부한다")
    void rejectsMalformed(String value) {
        // given / when / then
        assertThatThrownBy(() -> cipher.decrypt(value, "context")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("알 수 없는 키 버전을 거부한다")
    void rejectsUnknownVersion() {
        // given
        String value = cipher.encrypt("private", "context").replaceFirst("v1", "v99");
        // when / then
        assertThatThrownBy(() -> cipher.decrypt(value, "context")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키 교체 후 이전 암호문을 읽고 새 값은 활성 버전으로 암호화한다")
    void rotatesKeys() {
        // given
        String old = cipher.encrypt("private", "context");
        byte[] nextKey = new byte[32];
        nextKey[0] = 1;
        var rotated = new AesGcmPiiCipher(Map.of("1", new byte[32], "2", nextKey), "2");
        // when / then
        assertThat(rotated.decrypt(old, "context")).isEqualTo("private");
        assertThat(rotated.encrypt("private", "context")).startsWith("v2.");
    }
}
