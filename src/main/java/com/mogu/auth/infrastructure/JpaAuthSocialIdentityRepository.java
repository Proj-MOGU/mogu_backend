package com.mogu.auth.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAuthSocialIdentityRepository
        extends JpaRepository<AuthSocialIdentityJpaEntity, AuthSocialIdentityId> {
    @Modifying
    @Query("delete from AuthSocialIdentityJpaEntity i where i.memberId = :memberId")
    int deleteByMemberId(@Param("memberId") UUID memberId);
}
