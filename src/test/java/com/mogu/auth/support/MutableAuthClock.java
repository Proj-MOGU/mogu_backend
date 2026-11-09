package com.mogu.auth.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/** PostgreSQL TIMESTAMPTZ 정밀도(마이크로초)에 맞춰 경계 테스트가 OS와 무관하게 동작하게 한다 */
public final class MutableAuthClock extends Clock {
    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.now().truncatedTo(ChronoUnit.MICROS));

    public void reset() {
        now.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    public void advance(Duration duration) {
        now.updateAndGet(value -> value.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }
}
