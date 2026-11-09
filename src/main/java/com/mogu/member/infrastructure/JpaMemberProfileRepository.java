package com.mogu.member.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaMemberProfileRepository extends JpaRepository<MemberProfileJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from MemberProfileJpaEntity profile where profile.memberId = :memberId")
    Optional<MemberProfileJpaEntity> findByIdForUpdate(@Param("memberId") UUID memberId);

    boolean existsByNicknameKeyAndMemberIdNot(String nicknameKey, UUID memberId);
}
