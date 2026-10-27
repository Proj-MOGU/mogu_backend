package com.mogu.auth.application;

import com.mogu.auth.domain.RefreshToken;
import com.mogu.auth.domain.RefreshTokenRepository;
import com.mogu.common.auth.MemberId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
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
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash;
        try {
            hash = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ignored) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
        var token = RefreshToken.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                memberId,
                hash,
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                ttl);
        repository.save(token);
        return new IssuedRefreshToken(value, token.expiresAt());
    }
}
