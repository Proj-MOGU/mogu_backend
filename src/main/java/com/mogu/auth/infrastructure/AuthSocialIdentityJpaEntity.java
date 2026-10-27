package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_social_identity")
@IdClass(AuthSocialIdentityId.class)
public class AuthSocialIdentityJpaEntity {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Id
    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    protected AuthSocialIdentityJpaEntity() {}

    AuthSocialIdentityJpaEntity(AuthProvider provider, String providerSubject, UUID memberId, Instant linkedAt) {
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.memberId = memberId;
        this.linkedAt = linkedAt;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }
}
