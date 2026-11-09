package com.mogu.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.common.auth.MemberId;
import com.mogu.member.domain.Bio;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.Nickname;
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
        assertThat(profile.nickname()).isNull();
        assertThat(profile.bio().value()).isNull();
        assertThat(profile.nicknameChangedAt()).isNull();
        assertThat(profile.updatedAt()).isNull();
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
        assertThat(entity.getNickname()).isNull();
        assertThat(entity.getNicknameKey()).isNull();
        assertThat(entity.getBio()).isNull();
        assertThat(entity.getNicknameChangedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("엔티티의 모든 프로필 필드를 도메인으로 복원한다")
    void mapsCompleteEntityToDomain() {
        // given
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberProfileJpaEntity entity = new MemberProfileJpaEntity(id, now.minusSeconds(60), now.minusSeconds(50));
        entity.updateProfile("Star12", "star12", "소개😀", now.minusSeconds(10), now);
        // when
        MemberProfile profile = mapper.toDomain(entity);
        // then
        assertThat(profile.memberId()).isEqualTo(new MemberId(id));
        assertThat(profile.registeredAt()).isEqualTo(now.minusSeconds(60));
        assertThat(profile.nickname()).isEqualTo(Nickname.of("Star12"));
        assertThat(profile.nickname().key()).isEqualTo("star12");
        assertThat(profile.bio()).isEqualTo(Bio.of("소개😀"));
        assertThat(profile.nicknameChangedAt()).isEqualTo(now.minusSeconds(10));
        assertThat(profile.updatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("도메인의 모든 프로필 필드를 엔티티로 변환한다")
    void mapsCompleteDomainToEntity() {
        // given
        MemberId id = new MemberId(UUID.randomUUID());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberProfile profile = MemberProfile.reconstitute(
                id, now.minusSeconds(60), Nickname.of("Sky12"), Bio.of("소개😀"), now.minusSeconds(10), now);
        // when
        MemberProfileJpaEntity entity = mapper.toJpaEntity(profile, now.minusSeconds(50));
        // then
        assertThat(entity.getId()).isEqualTo(id.value());
        assertThat(entity.getRegisteredAt()).isEqualTo(now.minusSeconds(60));
        assertThat(entity.getCreatedAt()).isEqualTo(now.minusSeconds(50));
        assertThat(entity.getNickname()).isEqualTo("Sky12");
        assertThat(entity.getNicknameKey()).isEqualTo("sky12");
        assertThat(entity.getBio()).isEqualTo("소개😀");
        assertThat(entity.getNicknameChangedAt()).isEqualTo(now.minusSeconds(10));
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("현재 입력 정책에 어긋나는 저장된 닉네임과 소개도 원문으로 복원한다")
    void restoresValuesWithoutInputPolicyValidation() {
        // given
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberProfileJpaEntity entity = new MemberProfileJpaEntity(id, now.minusSeconds(60), now);
        String bio = "  한 소개\n  ";
        entity.updateProfile("Admin1", "admin1", bio, now.minusSeconds(10), now);
        // when
        MemberProfile profile = mapper.toDomain(entity);
        // then
        assertThat(profile.memberId()).isEqualTo(new MemberId(id));
        assertThat(profile.registeredAt()).isEqualTo(now.minusSeconds(60));
        assertThat(profile.nickname().value()).isEqualTo("Admin1");
        assertThat(profile.nickname().key()).isEqualTo("admin1");
        assertThat(profile.bio().value()).isEqualTo(bio);
        assertThat(profile.nicknameChangedAt()).isEqualTo(now.minusSeconds(10));
        assertThat(profile.updatedAt()).isEqualTo(now);
    }
}
