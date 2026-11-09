package com.mogu.member.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMemberProfileRepository extends JpaRepository<MemberProfileJpaEntity, UUID> {}
