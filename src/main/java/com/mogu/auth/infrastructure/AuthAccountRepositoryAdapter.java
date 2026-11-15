package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.DiHash;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import java.time.Instant;
import java.util.List;
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
    public Optional<AuthAccount> findByMemberIdForUpdate(MemberId memberId) {
        return accounts.findByMemberIdForUpdate(memberId.value()).map(mapper::toDomain);
    }

    @Override
    public int clearExpiredWithdrawnDiHashes(Instant withdrawnBefore) {
        return accounts.clearExpiredWithdrawnDiHashes(withdrawnBefore);
    }

    /** 기존 행을 잠근 뒤 탈퇴 계정을 비탈퇴 상태로 덮어쓰는 저장은 거절한다. */
    @Override
    public void save(AuthAccount account) {
        var entity = mapper.toJpaEntity(account);
        // 조회에서 생략된 복호화 실패 값은 다른 상태 저장 때문에 삭제하지 않는다.
        var previous = accounts.findByMemberIdForUpdate(entity.getMemberId());
        if (previous.isPresent() && previous.get().getStatus() == AccountStatus.WITHDRAWN && !account.isWithdrawn())
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        String nameEnc = account.isWithdrawn()
                ? null
                : entity.getNameEnc() != null
                        ? entity.getNameEnc()
                        : previous.map(AuthAccountJpaEntity::getNameEnc).orElse(null);
        String phoneEnc = account.isWithdrawn()
                ? null
                : entity.getPhoneEnc() != null
                        ? entity.getPhoneEnc()
                        : previous.map(AuthAccountJpaEntity::getPhoneEnc).orElse(null);
        accounts.saveAndFlush(new AuthAccountJpaEntity(
                entity.getMemberId(),
                entity.getDiHash(),
                entity.getStatus(),
                entity.getSanctionedUntil(),
                entity.getCreatedAt(),
                List.of(),
                nameEnc,
                phoneEnc,
                entity.getWithdrawnAt()));
        if (account.socialIdentities().isEmpty()) {
            identities.deleteByMemberId(entity.getMemberId());
            return;
        }
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
