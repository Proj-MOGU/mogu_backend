package com.mogu.member.infrastructure;

import com.mogu.common.auth.MemberId;
import com.mogu.member.domain.Bio;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.Nickname;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MemberProfileMapper {
    public MemberProfile toDomain(MemberProfileJpaEntity entity) {
        return MemberProfile.reconstitute(
                new MemberId(entity.getId()),
                entity.getRegisteredAt(),
                entity.getNickname() == null ? null : Nickname.reconstitute(entity.getNickname()),
                Bio.reconstitute(entity.getBio()),
                entity.getNicknameChangedAt(),
                entity.getUpdatedAt());
    }

    public MemberProfileJpaEntity toJpaEntity(MemberProfile profile, Instant createdAt) {
        MemberProfileJpaEntity entity =
                new MemberProfileJpaEntity(profile.memberId().value(), profile.registeredAt(), createdAt);
        updateEntity(profile, entity);
        return entity;
    }

    void updateEntity(MemberProfile profile, MemberProfileJpaEntity entity) {
        entity.updateProfile(
                profile.nickname() == null ? null : profile.nickname().value(),
                profile.nickname() == null ? null : profile.nickname().key(),
                profile.bio().value(),
                profile.nicknameChangedAt(),
                profile.updatedAt());
    }
}
