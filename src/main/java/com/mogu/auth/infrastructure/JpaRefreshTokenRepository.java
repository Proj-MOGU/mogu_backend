package com.mogu.auth.infrastructure;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity t SET t.usedAt = :now WHERE t.tokenHash = :hash AND t.usedAt IS NULL AND"
            + " t.revokedAt IS NULL AND t.expiresAt > :now")
    int markUsedIfActive(@Param("hash") String tokenHash, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity t SET t.revokedAt = :now WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshTokenJpaEntity t WHERE t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshTokenJpaEntity t SET t.revokedAt = :now WHERE t.memberId = :memberId AND t.revokedAt IS"
            + " NULL AND t.usedAt IS NULL AND t.expiresAt > :now")
    int revokeAllActiveByMember(@Param("memberId") UUID memberId, @Param("now") Instant now);
}
