package com.mogu.auth.domain;

import com.mogu.common.auth.MemberId;
import java.time.Instant;

public interface RefreshTokenRepository {
    void save(RefreshToken token);

    int revokeAllActiveByMember(MemberId memberId, Instant now);
}
