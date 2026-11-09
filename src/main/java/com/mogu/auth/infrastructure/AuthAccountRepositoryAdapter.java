package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.DiHash;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.auth.MemberId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AuthAccountRepositoryAdapter implements AuthAccountRepository {
    private final JpaAuthAccountRepository accounts;
    private final JpaAuthSocialIdentityRepository identities;
    private final AuthAccountMapper mapper;

    public AuthAccountRepositoryAdapter(
            JpaAuthAccountRepository accounts, JpaAuthSocialIdentityRepository identities, AuthAccountMapper mapper) {
        this.accounts = accounts;
        this.identities = identities;
        this.mapper = mapper;
    }

    @Override
    public Optional<AuthAccount> findBySocialIdentity(SocialIdentity identity) {
        return identities
                .findById(new AuthSocialIdentityId(identity.provider(), identity.subject()))
                .flatMap(i -> accounts.findById(i.getMemberId()))
                .map(mapper::toDomain);
    }

    @Override
    public Optional<AuthAccount> findByDiHash(DiHash diHash) {
        return accounts.findByDiHash(diHash.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<AuthAccount> findByMemberId(MemberId memberId) {
        return accounts.findById(memberId.value()).map(mapper::toDomain);
    }

    @Override
    public void save(AuthAccount account) {
        var entity = mapper.toJpaEntity(account);
        accounts.saveAndFlush(new AuthAccountJpaEntity(
                entity.getMemberId(),
                entity.getDiHash(),
                entity.getStatus(),
                entity.getSanctionedUntil(),
                entity.getCreatedAt()));
        identities.saveAllAndFlush(entity.getSocialIdentities());
    }
}
