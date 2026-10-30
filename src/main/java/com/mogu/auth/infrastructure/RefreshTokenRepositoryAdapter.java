package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.RefreshToken;
import com.mogu.auth.domain.RefreshTokenRepository;
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
    public void save(RefreshToken token) {
        repository.save(mapper.toJpaEntity(token));
    }
}
