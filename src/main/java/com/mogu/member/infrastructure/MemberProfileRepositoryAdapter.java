package com.mogu.member.infrastructure;

import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.member.domain.MemberErrorCode;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.MemberProfileRepository;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class MemberProfileRepositoryAdapter implements MemberProfileRepository {
    private final JpaMemberProfileRepository repository;
    private final MemberProfileMapper mapper;

    public MemberProfileRepositoryAdapter(JpaMemberProfileRepository repository, MemberProfileMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<MemberProfile> findById(MemberId memberId) {
        return repository.findById(memberId.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<MemberProfile> findByIdForUpdate(MemberId memberId) {
        return repository.findByIdForUpdate(memberId.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByNicknameKeyAndMemberIdNot(String nicknameKey, MemberId memberId) {
        return repository.existsByNicknameKeyAndMemberIdNot(nicknameKey, memberId.value());
    }

    @Override
    public void update(MemberProfile profile) {
        MemberProfileJpaEntity entity = repository
                .findByIdForUpdate(profile.memberId().value())
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
