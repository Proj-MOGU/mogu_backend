package com.mogu.auth.domain;

import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.Optional;

public interface AuthAccountRepository {
    Optional<AuthAccount> findBySocialIdentity(SocialIdentity identity);

    Optional<AuthAccount> findByDiHash(DiHash diHash);

    Optional<AuthAccount> findByMemberId(MemberId memberId);

    Optional<AuthAccount> findByMemberIdForUpdate(MemberId memberId);

    int clearExpiredWithdrawnDiHashes(Instant withdrawnBefore);

    /** 변경을 즉시 flush하여 같은 트랜잭션의 후속 가입 전에 UNIQUE 값을 해제한다. */
    void save(AuthAccount account);
}
