package com.mogu.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.common.auth.MemberId;
import com.mogu.member.domain.MemberProfile;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberProfileMapperTest {
    private final MemberProfileMapper mapper = new MemberProfileMapper();

    @Test
    @DisplayName("저장된 엔티티의 식별자와 가입 시각으로 도메인을 복원한다")
    void mapsToDomain() {
        // given
        UUID memberId = UUID.randomUUID();
        Instant registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant createdAt = registeredAt.plusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        MemberProfileJpaEntity entity = new MemberProfileJpaEntity(memberId, registeredAt, createdAt);

        // when
        MemberProfile profile = mapper.toDomain(entity);

        // then
        assertThat(profile.memberId()).isEqualTo(new MemberId(memberId));
        assertThat(profile.registeredAt()).isEqualTo(registeredAt);
    }

    @Test
    @DisplayName("도메인과 저장 시각을 삽입 전용 엔티티로 변환한다")
    void mapsToJpaEntity() {
        // given
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant createdAt = registeredAt.plusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        MemberProfile profile = MemberProfile.create(memberId, registeredAt);

        // when
        MemberProfileJpaEntity entity = mapper.toJpaEntity(profile, createdAt);

        // then
        assertThat(entity.getId()).isEqualTo(memberId.value());
        assertThat(entity.getRegisteredAt()).isEqualTo(registeredAt);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.isNew()).isTrue();
    }
}
