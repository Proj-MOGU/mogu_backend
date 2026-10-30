package com.mogu.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAuthSocialIdentityRepository
        extends JpaRepository<AuthSocialIdentityJpaEntity, AuthSocialIdentityId> {}
