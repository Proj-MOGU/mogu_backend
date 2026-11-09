package com.mogu.member.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "member_profile")
public class MemberProfileJpaEntity implements Persistable<UUID> {
    @Id
    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MemberProfileJpaEntity() {}

    MemberProfileJpaEntity(UUID memberId, Instant registeredAt, Instant createdAt) {
        this.memberId = Objects.requireNonNull(memberId);
        this.registeredAt = Objects.requireNonNull(registeredAt);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    @Override
    public UUID getId() {
        return memberId;
    }

    @Override
    @Transient
    public boolean isNew() {
        // 이번 저장 계약은 INSERT 전용이다. 같은 ID를 merge하여 기존 값을 덮어쓰지 않는다.
        return true;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
