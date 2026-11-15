package com.mogu.member.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaMemberProfileRepository extends JpaRepository<MemberProfileJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from MemberProfileJpaEntity profile where profile.memberId = :memberId")
    Optional<MemberProfileJpaEntity> findByIdForUpdate(@Param("memberId") UUID memberId);

    // 한 문장의 스냅숏으로 탈퇴 커밋 전 프로필 또는 커밋 후 묶음을 확인한다.
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM member_profile WHERE nickname_key = :nicknameKey AND member_id <> :memberId
        ) OR EXISTS (
            SELECT 1 FROM member_nickname_hold WHERE nickname_key = :nicknameKey AND released_at > :now
        )
        """, nativeQuery = true)
    boolean isNicknameUnavailable(
            @Param("nicknameKey") String nicknameKey, @Param("memberId") UUID memberId, @Param("now") Instant now);

    @Modifying
    @Query(value = """
        INSERT INTO member_nickname_hold (nickname_key, released_at) VALUES (:nicknameKey, :releasedAt)
        ON CONFLICT (nickname_key) DO UPDATE SET released_at = EXCLUDED.released_at
        """, nativeQuery = true)
    void holdNickname(@Param("nicknameKey") String nicknameKey, @Param("releasedAt") Instant releasedAt);
}
