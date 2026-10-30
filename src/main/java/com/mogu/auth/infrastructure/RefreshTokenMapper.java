package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.RefreshToken;
import com.mogu.common.auth.MemberId;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenMapper {
    public RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.reconstitute(
                entity.getId(),
                entity.getFamilyId(),
                new MemberId(entity.getMemberId()),
                entity.getTokenHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getUsedAt(),
                entity.getRevokedAt());
    }

    public RefreshTokenJpaEntity toJpaEntity(RefreshToken token) {
        return new RefreshTokenJpaEntity(
                token.id(),
                token.familyId(),
                token.memberId().value(),
                token.tokenHash(),
                token.issuedAt(),
                token.expiresAt(),
                token.usedAt(),
                token.revokedAt());
    }
}
