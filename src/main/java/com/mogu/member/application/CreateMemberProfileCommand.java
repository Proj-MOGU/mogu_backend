package com.mogu.member.application;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.Objects;

public record CreateMemberProfileCommand(MemberId memberId, Instant registeredAt) {
    public CreateMemberProfileCommand {
        Objects.requireNonNull(memberId, "memberId is required");
        Objects.requireNonNull(registeredAt, "registeredAt is required");
    }
}
