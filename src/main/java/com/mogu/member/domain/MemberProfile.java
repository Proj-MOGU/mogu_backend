package com.mogu.member.domain;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.Objects;

public class MemberProfile {
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

    public MemberId memberId() {
        return memberId;
    }

    public Instant registeredAt() {
        return registeredAt;
    }
}
