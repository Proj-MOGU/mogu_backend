package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.RefreshToken;
import com.mogu.auth.domain.RefreshTokenRepository;
import com.mogu.common.auth.MemberId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {
    private final JpaRefreshTokenRepository repository;
    private final RefreshTokenMapper mapper;

    public RefreshTokenRepositoryAdapter(JpaRefreshTokenRepository repository, RefreshTokenMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public boolean markUsedIfActive(String tokenHash, Instant now) {
        return repository.markUsedIfActive(tokenHash, now) == 1;
    }

    @Override
    public int revokeFamily(UUID familyId, Instant now) {
        return repository.revokeFamily(familyId, now);
    }

    @Override
    public int deleteExpiredBefore(Instant threshold) {
        return repository.deleteExpiredBefore(threshold);
    }

    @Override
    public void save(RefreshToken token) {
        repository.save(mapper.toJpaEntity(token));
    }

    @Override
    public int revokeAllActiveByMember(MemberId memberId, Instant now) {
        return repository.revokeAllActiveByMember(memberId.value(), now);
    }
}
