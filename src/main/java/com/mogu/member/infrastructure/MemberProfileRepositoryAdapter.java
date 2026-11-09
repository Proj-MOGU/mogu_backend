package com.mogu.member.infrastructure;

import com.mogu.common.auth.MemberId;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.MemberProfileRepository;
import java.time.Instant;
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
    public boolean existsById(MemberId memberId) {
        return repository.existsById(memberId.value());
    }

    @Override
    public void save(MemberProfile profile, Instant createdAt) {
        repository.saveAndFlush(mapper.toJpaEntity(profile, createdAt));
    }
}
