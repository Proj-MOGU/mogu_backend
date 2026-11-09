package com.mogu.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAuthAccountRepository extends JpaRepository<AuthAccountJpaEntity, UUID> {
    Optional<AuthAccountJpaEntity> findByDiHash(String diHash);
}
