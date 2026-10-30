package com.mogu.auth.domain;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class AuthAccount {
    private final MemberId memberId;
    private final AccountStatus status;
    private final Instant sanctionedUntil;
    private final Instant createdAt;
    private final List<LinkedSocialIdentity> socialIdentities;

    private AuthAccount(
            MemberId memberId,
            AccountStatus status,
            Instant sanctionedUntil,
            Instant createdAt,
            List<LinkedSocialIdentity> socialIdentities) {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        if (status != AccountStatus.SANCTIONED && sanctionedUntil != null)
            throw new IllegalArgumentException("Invalid sanction state");
        this.memberId = memberId;
        this.status = status;
        this.sanctionedUntil = sanctionedUntil;
        this.createdAt = createdAt;
        this.socialIdentities = List.copyOf(socialIdentities);
    }

    public static AuthAccount reconstitute(
            MemberId memberId,
            AccountStatus status,
            Instant sanctionedUntil,
            Instant createdAt,
            List<LinkedSocialIdentity> socialIdentities) {
        return new AuthAccount(memberId, status, sanctionedUntil, createdAt, socialIdentities);
    }

    public MemberId memberId() {
        return memberId;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant sanctionedUntil() {
        return sanctionedUntil;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<LinkedSocialIdentity> socialIdentities() {
        return socialIdentities;
    }

    public boolean isSanctionedAt(Instant now) {
        Objects.requireNonNull(now);
        return status == AccountStatus.SANCTIONED && (sanctionedUntil == null || sanctionedUntil.isAfter(now));
    }
}
