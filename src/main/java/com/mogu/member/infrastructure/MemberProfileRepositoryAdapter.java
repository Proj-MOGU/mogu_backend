package com.mogu.member.infrastructure;

import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.member.domain.MemberErrorCode;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.MemberProfileRepository;
import com.mogu.member.domain.Nickname;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class MemberProfileRepositoryAdapter implements MemberProfileRepository {
    private final JpaMemberProfileRepository repository;
    private final MemberProfileMapper mapper;
    private final EntityManager entityManager;

    public MemberProfileRepositoryAdapter(
            JpaMemberProfileRepository repository, MemberProfileMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<MemberProfile> findById(MemberId memberId) {
        return repository.findById(memberId.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<MemberProfile> findByIdForUpdate(MemberId memberId) {
        return findLockedEntity(memberId).map(mapper::toDomain);
    }

    @Override
    public boolean isNicknameUnavailable(Nickname nickname, MemberId memberId, Instant now) {
        return repository.isNicknameUnavailable(nickname.key(), memberId.value(), now);
    }

    @Override
    public void holdNickname(Nickname nickname, Instant releasedAt) {
        repository.holdNickname(nickname.key(), releasedAt);
    }

    @Override
    public void delete(MemberId memberId) {
        findLockedEntity(memberId).ifPresent(entity -> {
            entityManager.remove(entity);
            repository.flush();
        });
    }

    private Optional<MemberProfileJpaEntity> findLockedEntity(MemberId memberId) {
        return repository.findByIdForUpdate(memberId.value()).map(entity -> {
            // 발행 트랜잭션에서 먼저 읽은 관리 엔티티도 DB의 최신 값으로 판단한다 (TS-0003).
            entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
            return entity;
        });
    }

    @Override
    public void update(MemberProfile profile) {
        MemberProfileJpaEntity entity = findLockedEntity(profile.memberId())
                .orElseThrow(() -> new BusinessException(MemberErrorCode.PROFILE_NOT_FOUND));
        mapper.updateEntity(profile, entity);
        try {
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uk_member_profile_nickname_key".equals(violation.getConstraintName()))
                    throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
            }
            throw exception;
        }
    }

    @Override
    public boolean existsById(MemberId memberId) {
        return repository.existsById(memberId.value());
    }

    @Override
    public void save(MemberProfile profile, Instant createdAt) {
        repository.saveAndFlush(mapper.toJpaEntity(profile, createdAt));
    }
}
