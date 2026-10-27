package com.mogu.auth.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_refresh_token")
public class RefreshTokenJpaEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at", nullable = true)
    private Instant usedAt;

    @Column(name = "revoked_at", nullable = true)
    private Instant revokedAt;

    protected RefreshTokenJpaEntity() {}

    RefreshTokenJpaEntity(
            UUID id,
            UUID familyId,
            UUID memberId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant usedAt,
            Instant revokedAt) {
        this.id = id;
        this.familyId = familyId;
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
