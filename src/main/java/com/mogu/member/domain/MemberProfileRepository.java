package com.mogu.member.domain;

import com.mogu.common.auth.MemberId;
import java.time.Instant;

public interface MemberProfileRepository {
    boolean existsById(MemberId memberId);

    void save(MemberProfile profile, Instant createdAt);
}
