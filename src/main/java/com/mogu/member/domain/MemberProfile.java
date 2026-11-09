package com.mogu.member.domain;

import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class MemberProfile {
    private static final Duration NICKNAME_CHANGE_INTERVAL = Duration.ofDays(30);
    private Nickname nickname;
    private Bio bio = Bio.of(null);
    private Instant nicknameChangedAt;
    private Instant updatedAt;
    private final MemberId memberId;
    private final Instant registeredAt;

    private MemberProfile(MemberId memberId, Instant registeredAt) {
        this.memberId = Objects.requireNonNull(memberId, "memberId is required");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt is required");
    }

    public static MemberProfile create(MemberId memberId, Instant registeredAt) {
        return new MemberProfile(memberId, registeredAt);
    }

    public static MemberProfile reconstitute(MemberId memberId, Instant registeredAt) {
        return new MemberProfile(memberId, registeredAt);
    }

    public static MemberProfile reconstitute(
            MemberId memberId,
            Instant registeredAt,
            Nickname nickname,
            Bio bio,
            Instant nicknameChangedAt,
            Instant updatedAt) {
        MemberProfile profile = new MemberProfile(memberId, registeredAt);
        profile.nickname = nickname;
        profile.bio = Objects.requireNonNull(bio);
        profile.nicknameChangedAt = nicknameChangedAt;
        profile.updatedAt = updatedAt;
        return profile;
    }

    public void updateProfile(Nickname nickname, Bio bio, Instant now) {
        if (nickname == null) throw new BusinessException(MemberErrorCode.INVALID_NICKNAME);
        Objects.requireNonNull(bio);
        Objects.requireNonNull(now);
        if (nickname.equals(this.nickname) && bio.equals(this.bio)) return;
        if (!nickname.equals(this.nickname)) {
            if (this.nickname != null) {
                if (nicknameChangeableAt(now).isPresent())
                    throw new BusinessException(MemberErrorCode.NICKNAME_CHANGE_TOO_SOON);
                nicknameChangedAt = now;
            }
            this.nickname = nickname;
        }
        this.bio = bio;
        updatedAt = now;
    }

    public boolean isCompleted() {
        return nickname != null;
    }

    public Optional<Instant> nicknameChangeableAt(Instant now) {
        return Optional.ofNullable(nicknameChangedAt)
                .map(time -> time.plus(NICKNAME_CHANGE_INTERVAL))
                .filter(time -> now.isBefore(time));
    }

    public Nickname nickname() {
        return nickname;
    }

    public Bio bio() {
        return bio;
    }

    public Instant nicknameChangedAt() {
        return nicknameChangedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public MemberId memberId() {
        return memberId;
    }

    public Instant registeredAt() {
        return registeredAt;
    }
}
