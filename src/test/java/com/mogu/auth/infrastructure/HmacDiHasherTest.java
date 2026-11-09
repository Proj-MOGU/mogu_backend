package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.auth.domain.DiHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacDiHasherTest {
    @Test
    @DisplayName("알려진 HMAC SHA256 벡터와 일치하며 입력과 키에 따라 해시가 달라진다")
    void hashesKnownVector() {
        // given
        byte[] key = new byte[32];
        var hasher = new HmacDiHasher(key);
        // when / then
        assertThat(hasher.hash("abc").value())
                .isEqualTo("fd7adb152c05ef80dccf50a1fa4c05d5a3ec6da95575fc312ae7c5d091836351");
        assertThat(hasher.hash("abc")).isEqualTo(hasher.hash("abc")).isNotEqualTo(hasher.hash("abcd"));
        key[0] = 1;
        assertThat(new HmacDiHasher(key).hash("abc")).isNotEqualTo(hasher.hash("abc"));
    }

    @Test
    @DisplayName("짧은 키와 빈 DI 및 잘못된 해시를 거부하고 해시는 앞 여덟 자만 표시한다")
    void validatesAndRedacts() {
        // given
        var hasher = new HmacDiHasher(new byte[32]);
        // when / then
        assertThatThrownBy(() -> new HmacDiHasher(new byte[31])).isInstanceOf(IllegalArgumentException.class);
        for (String value : new String[] {null, "", " "})
            assertThatThrownBy(() -> hasher.hash(value)).isInstanceOf(IllegalArgumentException.class);
        for (String value : new String[] {null, "", "A".repeat(64), "a".repeat(63)})
            assertThatThrownBy(() -> new DiHash(value)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DiHash("a".repeat(64)).toString()).isEqualTo("aaaaaaaa…");
    }
}
