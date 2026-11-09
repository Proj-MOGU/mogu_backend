package com.mogu.auth.application;

import com.mogu.auth.domain.RefreshToken;
import com.mogu.auth.domain.RefreshTokenRepository;
import com.mogu.common.auth.MemberId;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenIssuer {
    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenIssuer(
            RefreshTokenRepository repository,
            @Qualifier("authClock") Clock clock,
            @Value("${mogu.auth.refresh-token.ttl:30d}") Duration ttl) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = ttl;
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("mogu.auth.refresh-token.ttl");
    }

    public IssuedRefreshToken issueNewFamily(MemberId memberId) {
        return issueInFamily(memberId, UUID.randomUUID());
    }

    public IssuedRefreshToken issueInFamily(MemberId memberId, UUID familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = RefreshTokenHasher.hash(value);
        var token = RefreshToken.issue(
                UUID.randomUUID(), familyId, memberId, hash, clock.instant().truncatedTo(ChronoUnit.MICROS), ttl);
        repository.save(token);
        return new IssuedRefreshToken(value, token.expiresAt());
    }
}
