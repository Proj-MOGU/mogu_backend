package com.mogu.auth.domain;

import com.mogu.common.auth.MemberId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class RefreshToken {
    private final UUID id;
    private final UUID familyId;
    private final MemberId memberId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant usedAt;
    private final Instant revokedAt;

    private RefreshToken(
            UUID id,
            UUID familyId,
            MemberId memberId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant usedAt,
            Instant revokedAt) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(familyId);
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(issuedAt);
        Objects.requireNonNull(expiresAt);
        if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}") || !expiresAt.isAfter(issuedAt))
            throw new IllegalArgumentException("Invalid refresh token state");
        this.id = id;
        this.familyId = familyId;
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
    }

    public static RefreshToken reconstitute(
            UUID id,
            UUID familyId,
            MemberId memberId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant usedAt,
            Instant revokedAt) {
        return new RefreshToken(id, familyId, memberId, tokenHash, issuedAt, expiresAt, usedAt, revokedAt);
    }

    public UUID id() {
        return id;
    }

    public UUID familyId() {
        return familyId;
    }

    public MemberId memberId() {
        return memberId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant usedAt() {
        return usedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public static RefreshToken issue(
            UUID id, UUID familyId, MemberId memberId, String tokenHash, Instant now, Duration ttl) {
        return new RefreshToken(id, familyId, memberId, tokenHash, now, now.plus(ttl), null, null);
    }
}
