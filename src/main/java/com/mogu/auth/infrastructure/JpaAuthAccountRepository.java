package com.mogu.auth.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAuthAccountRepository extends JpaRepository<AuthAccountJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AuthAccountJpaEntity a where a.memberId = :memberId")
    Optional<AuthAccountJpaEntity> findByMemberIdForUpdate(@Param("memberId") UUID memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "UPDATE auth_account SET di_hash = NULL WHERE status = 'WITHDRAWN' AND withdrawn_at < :before AND"
                    + " di_hash IS NOT NULL",
            nativeQuery = true)
    int clearExpiredWithdrawnDiHashes(@Param("before") Instant before);

    Optional<AuthAccountJpaEntity> findByDiHash(String diHash);
}
