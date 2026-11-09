package com.mogu.member.application;

import com.mogu.common.auth.MemberId;
import com.mogu.member.domain.MemberProfile;
import java.time.Instant;

public record MyProfileResult(
        MemberId memberId,
        String nickname,
        String bio,
        Instant registeredAt,
        boolean completed,
        Instant nicknameChangeableAt) {
    static MyProfileResult from(MemberProfile profile, Instant now) {
        return new MyProfileResult(
                profile.memberId(),
                profile.nickname() == null ? null : profile.nickname().value(),
                profile.bio().value(),
                profile.registeredAt(),
                profile.isCompleted(),
                profile.nicknameChangeableAt(now).orElse(null));
    }
}
