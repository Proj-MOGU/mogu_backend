package com.mogu.member.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
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

    @Column(name = "nickname", length = 12)
    private String nickname;

    @Column(name = "nickname_key", length = 12)
    private String nicknameKey;

    @Column(name = "bio", length = 50)
    private String bio;

    @Column(name = "nickname_changed_at")
    private Instant nicknameChangedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Transient
    private boolean newEntity = true;

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    void updateProfile(String nickname, String nicknameKey, String bio, Instant nicknameChangedAt, Instant updatedAt) {
        this.nickname = nickname;
        this.nicknameKey = nicknameKey;
        this.bio = bio;
        this.nicknameChangedAt = nicknameChangedAt;
        this.updatedAt = updatedAt;
    }

    public String getNickname() {
        return nickname;
    }

    public String getNicknameKey() {
        return nicknameKey;
    }

    public String getBio() {
        return bio;
    }

    public Instant getNicknameChangedAt() {
        return nicknameChangedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

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
        return newEntity;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
