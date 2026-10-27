package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.LinkedSocialIdentity;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.auth.MemberId;
import org.springframework.stereotype.Component;

@Component
public class AuthAccountMapper {
    public AuthAccount toDomain(AuthAccountJpaEntity entity) {
        return AuthAccount.reconstitute(
                new MemberId(entity.getMemberId()),
                entity.getStatus(),
                entity.getSanctionedUntil(),
                entity.getCreatedAt(),
                entity.getSocialIdentities().stream()
                        .map(i -> new LinkedSocialIdentity(
                                new SocialIdentity(i.getProvider(), i.getProviderSubject()), i.getLinkedAt()))
                        .toList());
    }

    public AuthAccountJpaEntity toJpaEntity(AuthAccount account) {
        return new AuthAccountJpaEntity(
                account.memberId().value(),
                account.status(),
                account.sanctionedUntil(),
                account.createdAt(),
                account.socialIdentities().stream()
                        .map(i -> new AuthSocialIdentityJpaEntity(
                                i.identity().provider(),
                                i.identity().subject(),
                                account.memberId().value(),
                                i.linkedAt()))
                        .toList());
    }
}
