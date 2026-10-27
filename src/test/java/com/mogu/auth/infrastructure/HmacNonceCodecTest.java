package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HmacNonceCodecTest {
    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    private HmacNonceCodec codec(Instant now) {
        return new HmacNonceCodec(new byte[32], Duration.ofMinutes(5), Clock.fixed(now, ZoneOffset.UTC));
    }

    private void rejected(String nonce) {
        assertThatThrownBy(() -> codec(NOW).verifyAndGetExpiry(nonce))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AuthErrorCode.INVALID_SOCIAL_TOKEN));
    }

    @Test
    @DisplayName("발급 형식과 만료 및 정상 검증을 확인한다")
    void issuesAndVerifies() {
        // given
        var codec = codec(NOW);
        // when
        var issued = codec.issue();
        // then
        assertThat(issued.value()).startsWith("n1.").hasSizeLessThanOrEqualTo(128);
        assertThat(issued.value().split("\\.")).hasSize(4);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(codec.verifyAndGetExpiry(issued.value())).isEqualTo(issued.expiresAt());
        assertThat(issued.toString()).doesNotContain(issued.value());
        assertThat(codec.issue().value()).isNotEqualTo(issued.value());
    }

    @Test
    @DisplayName("만료 시각에는 유효하고 1초 후에는 거부한다")
    void expiresWithoutSkew() {
        // given
        var issued = codec(NOW.minusSeconds(300)).issue();
        // when / then
        assertThat(codec(NOW).verifyAndGetExpiry(issued.value())).isEqualTo(NOW);
        rejected(codec(NOW.minusSeconds(301)).issue().value());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("난수와 만료 시각과 MAC 변조를 거부한다")
    void rejectsTampering(int part) {
        // given
        var parts = codec(NOW).issue().value().split("\\.");
        parts[part] = (parts[part].charAt(0) == '1' ? "2" : "1") + parts[part].substring(1);
        // when / then
        rejected(String.join(".", parts));
    }

    @Test
    @DisplayName("다른 비밀키와 지나치게 먼 미래의 서명을 거부한다")
    void rejectsOtherKeyAndFuture() {
        // given
        byte[] key = new byte[32];
        key[0] = 1;
        var other = new HmacNonceCodec(key, Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));
        // when / then
        rejected(other.issue().value());
        rejected(codec(NOW.plusSeconds(61)).issue().value());
        assertThat(codec(NOW)
                        .verifyAndGetExpiry(codec(NOW.plusSeconds(60)).issue().value()))
                .isEqualTo(NOW.plusSeconds(360));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "broken",
                "n2.a.b.c",
                "n1.a.b.c",
                "n1....",
                " ",
                "n1.AAAAAAAAAAAAAAAAAAAAAA.9999999999999999999.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            })
    @DisplayName("깨진 형식과 빈 입력을 거부한다")
    void rejectsMalformed(String value) {
        // given / when / then
        rejected(value);
    }

    @Test
    @DisplayName("129자 입력을 거부한다")
    void rejectsOversized() {
        // given / when / then
        rejected("a".repeat(129));
    }
}
