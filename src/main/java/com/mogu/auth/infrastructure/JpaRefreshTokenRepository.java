package com.mogu.auth.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {}
