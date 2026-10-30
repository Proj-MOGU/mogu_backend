package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberIdGeneratorTest {
    @Test
    @DisplayName("같은 밀리초에 생성한 UUID 천 개는 버전 7과 표준 variant이며 중복이 없다")
    void generatesUniqueVersionSeven() {
        // given
        var now = Instant.parse("2026-10-30T00:00:00Z");
        var generator = new UuidV7MemberIdGenerator(Clock.fixed(now, ZoneOffset.UTC));
        var ids = new HashSet<UUID>();
        // when / then
        for (int i = 0; i < 1000; i++) {
            var id = generator.generate().value();
            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
            assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
            ids.add(id);
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    @DisplayName("밀리초가 다르면 UUID 정렬은 생성 시간 순서를 따른다")
    void ordersByMilliseconds() {
        // given
        var now = Instant.parse("2026-10-30T00:00:00Z");
        UUID previous = new UuidV7MemberIdGenerator(Clock.fixed(now, ZoneOffset.UTC))
                .generate()
                .value();
        // when / then
        for (int i = 1; i <= 1000; i++) {
            UUID next = new UuidV7MemberIdGenerator(Clock.fixed(now.plusMillis(i), ZoneOffset.UTC))
                    .generate()
                    .value();
            assertThat(next).isGreaterThan(previous);
            previous = next;
        }
    }
}
