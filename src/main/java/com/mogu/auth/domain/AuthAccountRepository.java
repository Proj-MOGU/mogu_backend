package com.mogu.auth.domain;

import java.util.Optional;

public interface AuthAccountRepository {
    Optional<AuthAccount> findBySocialIdentity(SocialIdentity identity);
}
