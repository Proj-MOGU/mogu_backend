package com.mogu.auth.domain;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    boolean markUsedIfActive(String tokenHash, Instant now);

    int revokeFamily(UUID familyId, Instant now);

    int deleteExpiredBefore(Instant threshold);

    void save(RefreshToken token);

    int revokeAllActiveByMember(MemberId memberId, Instant now);
}
