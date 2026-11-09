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
        // 조회에서 생략된 복호화 실패 값은 다른 상태 저장 때문에 삭제하지 않는다.
        var previous = accounts.findById(entity.getMemberId());
        String nameEnc = entity.getNameEnc() != null
                ? entity.getNameEnc()
                : previous.map(AuthAccountJpaEntity::getNameEnc).orElse(null);
        String phoneEnc = entity.getPhoneEnc() != null
                ? entity.getPhoneEnc()
                : previous.map(AuthAccountJpaEntity::getPhoneEnc).orElse(null);
        accounts.saveAndFlush(new AuthAccountJpaEntity(
                entity.getMemberId(),
                entity.getDiHash(),
                entity.getStatus(),
                entity.getSanctionedUntil(),
                entity.getCreatedAt(),
                java.util.List.of(),
                nameEnc,
                phoneEnc));
        var linked = entity.getSocialIdentities().stream()
                .map(i -> {
                    if (i.getEmailEnc() != null) return i;
                    var old = identities.findById(new AuthSocialIdentityId(i.getProvider(), i.getProviderSubject()));
                    return new AuthSocialIdentityJpaEntity(
                            i.getProvider(),
                            i.getProviderSubject(),
                            i.getMemberId(),
                            i.getLinkedAt(),
                            old.map(AuthSocialIdentityJpaEntity::getEmailEnc).orElse(null),
                            old.map(AuthSocialIdentityJpaEntity::isEmailVerified)
                                    .orElse(false));
                })
                .toList();
        identities.saveAllAndFlush(linked);
    }
}
