package com.mogu.common.event;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.Objects;

/** 인증팀 발행, 회원팀 구독: 회원이 탈퇴했다. */
public record MemberWithdrawn(MemberId memberId, Instant withdrawnAt) {
    public MemberWithdrawn {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(withdrawnAt);
    }
}
