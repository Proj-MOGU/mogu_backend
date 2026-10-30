package com.mogu.common.event;

import com.mogu.common.auth.MemberId;
import java.util.Objects;

/** 신고팀 발행, 인증팀 구독: 회원의 제재를 해제한다. */
public record MemberSanctionLifted(MemberId memberId) {
    public MemberSanctionLifted {
        Objects.requireNonNull(memberId);
    }
}
