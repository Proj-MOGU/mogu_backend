package com.mogu.auth.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
    @Modifying(flushAutomatically = true)
    @Query(
            "UPDATE RefreshTokenJpaEntity t SET t.revokedAt = :now WHERE t.memberId = :memberId AND t.revokedAt IS NULL AND t.usedAt IS NULL AND t.expiresAt > :now")
    int revokeAllActiveByMember(@Param("memberId") UUID memberId, @Param("now") Instant now);
}
