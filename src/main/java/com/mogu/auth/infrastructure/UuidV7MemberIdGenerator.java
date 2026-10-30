package com.mogu.auth.infrastructure;

import com.mogu.auth.application.MemberIdGenerator;
import com.mogu.common.auth.MemberId;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public final class UuidV7MemberIdGenerator implements MemberIdGenerator {
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UuidV7MemberIdGenerator(@Qualifier("authClock") Clock clock) {
        this.clock = clock;
    }

    @Override
    public MemberId generate() {
        long ms = clock.millis();
        long high = ((ms & 0xffffffffffffL) << 16) | 0x7000L | (random.nextLong() & 0xfffL);
        long low = (random.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new MemberId(new UUID(high, low));
    }
}
