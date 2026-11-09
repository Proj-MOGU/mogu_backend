package com.mogu.member.infrastructure;

import com.mogu.common.auth.MemberId;
import com.mogu.member.domain.MemberProfile;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MemberProfileMapper {
    public MemberProfile toDomain(MemberProfileJpaEntity entity) {
        return MemberProfile.reconstitute(new MemberId(entity.getId()), entity.getRegisteredAt());
    }

    public MemberProfileJpaEntity toJpaEntity(MemberProfile profile, Instant createdAt) {
        return new MemberProfileJpaEntity(profile.memberId().value(), profile.registeredAt(), createdAt);
    }
}
