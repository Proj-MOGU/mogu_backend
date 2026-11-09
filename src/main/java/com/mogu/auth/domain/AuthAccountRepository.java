package com.mogu.auth.domain;

import com.mogu.common.auth.MemberId;
import java.util.Optional;

public interface AuthAccountRepository {
    Optional<AuthAccount> findBySocialIdentity(SocialIdentity identity);

    Optional<AuthAccount> findByDiHash(DiHash diHash);

    Optional<AuthAccount> findByMemberId(MemberId memberId);

    void save(AuthAccount account);
}
