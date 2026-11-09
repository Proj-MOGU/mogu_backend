package com.mogu.member.domain;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.Optional;

public interface MemberProfileRepository {
    Optional<MemberProfile> findById(MemberId memberId);

    Optional<MemberProfile> findByIdForUpdate(MemberId memberId);

    boolean existsByNicknameKeyAndMemberIdNot(String nicknameKey, MemberId memberId);

    void update(MemberProfile profile);

    boolean existsById(MemberId memberId);

    void save(MemberProfile profile, Instant createdAt);
}
