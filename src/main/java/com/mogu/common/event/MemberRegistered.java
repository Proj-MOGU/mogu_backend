package com.mogu.common.event;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.Objects;

/** 인증팀 발행, 회원팀 구독: 본인인증을 마친 신규 회원이 등록되었다. */
public record MemberRegistered(MemberId memberId, Instant registeredAt) {
    public MemberRegistered {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(registeredAt);
    }
}
