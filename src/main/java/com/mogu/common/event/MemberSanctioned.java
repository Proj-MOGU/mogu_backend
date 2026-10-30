package com.mogu.common.event;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.Objects;

/** 신고팀 발행, 인증팀 구독: 회원을 지정 기한까지 제재하며 null은 무기한이다. */
public record MemberSanctioned(MemberId memberId, Instant until) {
    public MemberSanctioned {
        Objects.requireNonNull(memberId);
    }
}
