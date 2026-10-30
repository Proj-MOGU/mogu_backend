package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "auth_account")
public class AuthAccountJpaEntity {
    @Id
    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "di_hash", nullable = false, unique = true, length = 64)
    private String diHash;

    public String getDiHash() {
        return diHash;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "sanctioned_until", nullable = true)
    private Instant sanctionedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany
    @JoinColumn(name = "member_id", insertable = false, updatable = false)
    private List<AuthSocialIdentityJpaEntity> socialIdentities = List.of();

    public List<AuthSocialIdentityJpaEntity> getSocialIdentities() {
        return List.copyOf(socialIdentities);
    }

    AuthAccountJpaEntity(
            UUID memberId,
            String diHash,
            AccountStatus status,
            Instant sanctionedUntil,
            Instant createdAt,
            List<AuthSocialIdentityJpaEntity> identities) {
        this(memberId, diHash, status, sanctionedUntil, createdAt);
        this.socialIdentities = List.copyOf(identities);
    }

    protected AuthAccountJpaEntity() {}

    AuthAccountJpaEntity(
            UUID memberId, String diHash, AccountStatus status, Instant sanctionedUntil, Instant createdAt) {
        this.memberId = memberId;
        this.diHash = diHash;
        this.status = status;
        this.sanctionedUntil = sanctionedUntil;
        this.createdAt = createdAt;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getSanctionedUntil() {
        return sanctionedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
