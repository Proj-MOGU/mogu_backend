package com.mogu.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.common.event.MemberRegistered;
import com.mogu.member.domain.Bio;
import com.mogu.member.domain.MemberErrorCode;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.MemberProfileRepository;
import com.mogu.member.domain.Nickname;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MemberProfileServiceTest {
    @Test
    @DisplayName("프로필이 없으면 가입 시각과 현재 저장 시각으로 저장한다")
    void savesMissingProfile() {
        // given
        FakeRepository repository = new FakeRepository();
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant createdAt = registeredAt.plusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        MemberProfileService service = new MemberProfileService(repository, Clock.fixed(createdAt, ZoneOffset.UTC));

        // when
        service.createIncompleteProfile(new CreateMemberProfileCommand(memberId, registeredAt));

        // then
        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(repository.profiles.get(memberId).memberId()).isEqualTo(memberId);
        assertThat(repository.profiles.get(memberId).registeredAt()).isEqualTo(registeredAt);
        assertThat(repository.createdTimes.get(memberId)).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("프로필이 있으면 다시 저장하지 않고 최초 값을 유지한다")
    void skipsExistingProfile() {
        // given
        FakeRepository repository = new FakeRepository();
        MemberId memberId = new MemberId(UUID.randomUUID());
        Instant registeredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant createdAt = registeredAt.plusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        MemberProfile existing = MemberProfile.create(memberId, registeredAt);
        repository.save(existing, createdAt);
        MemberProfileService service =
                new MemberProfileService(repository, Clock.fixed(createdAt.plusSeconds(60), ZoneOffset.UTC));

        // when
        service.createIncompleteProfile(new CreateMemberProfileCommand(memberId, registeredAt.plusSeconds(30)));

        // then
        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(repository.profiles.get(memberId)).isSameAs(existing);
        assertThat(repository.profiles.get(memberId).registeredAt()).isEqualTo(registeredAt);
        assertThat(repository.createdTimes.get(memberId)).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("저장 실패는 이벤트 발행자에게 그대로 전파한다")
    void propagatesSaveFailureToPublisher() {
        // given
        IllegalStateException failure = new IllegalStateException("storage unavailable");
        MemberProfileRepository repository = new FakeRepository() {
            @Override
            public void save(MemberProfile profile, Instant createdAt) {
                throw failure;
            }
        };
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberProfileService service = new MemberProfileService(repository, Clock.fixed(now, ZoneOffset.UTC));
        MemberRegisteredEventListener listener = new MemberRegisteredEventListener(service);
        var event = new MemberRegistered(new MemberId(UUID.randomUUID()), now);

        // when / then
        assertThatThrownBy(() -> listener.onMemberRegistered(event)).isSameAs(failure);
    }

    @Test
    @DisplayName("본인 프로필 조회는 미완성 상태와 가입 시각을 반환한다")
    void readsOwnIncompleteProfile() {
        // given
        FakeRepository repository = new FakeRepository();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberId id = new MemberId(UUID.randomUUID());
        repository.save(MemberProfile.create(id, now), now);
        MemberProfileService service = new MemberProfileService(repository, Clock.fixed(now, ZoneOffset.UTC));
        // when
        MyProfileResult result = service.getMyProfile(id);
        // then
        assertThat(result.memberId()).isEqualTo(id);
        assertThat(result.registeredAt()).isEqualTo(now);
        assertThat(result.completed()).isFalse();
        assertThat(result.nickname()).isNull();
        assertThat(result.nicknameChangeableAt()).isNull();
    }

    @Test
    @DisplayName("본인 수정은 잠금 조회를 사용하고 정규화된 값을 저장한다")
    void updatesUsingLockedRead() {
        // given
        FakeRepository repository = new FakeRepository();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberId id = new MemberId(UUID.randomUUID());
        repository.save(MemberProfile.create(id, now), now);
        MemberProfileService service = new MemberProfileService(repository, Clock.fixed(now, ZoneOffset.UTC));
        String nickname = "N" + UUID.randomUUID().toString().substring(0, 8);
        // when
        MyProfileResult result =
                service.updateMyProfile(new UpdateMyProfileCommand(id, "  " + nickname + "  ", " 소개 "));
        // then
        assertThat(repository.lockedReadCount).isEqualTo(1);
        assertThat(result.nickname()).isEqualTo(nickname);
        assertThat(result.bio()).isEqualTo("소개");
        assertThat(result.completed()).isTrue();
        assertThat(result.nicknameChangeableAt()).isNull();
        assertThat(service.getPublicProfile(id)).isEqualTo(new PublicProfileResult(id, nickname, "소개"));
        assertThat(service.updateMyProfile(new UpdateMyProfileCommand(id, nickname, null))
                        .bio())
                .isNull();
    }

    @Test
    @DisplayName("다른 회원과 중복되면 거부하고 본인의 같은 닉네임은 허용한다")
    void checksNicknameOwnership() {
        // given
        FakeRepository repository = new FakeRepository();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberId first = new MemberId(UUID.randomUUID());
        MemberId second = new MemberId(UUID.randomUUID());
        repository.save(MemberProfile.create(first, now), now);
        repository.save(MemberProfile.create(second, now), now);
        MemberProfileService service = new MemberProfileService(repository, Clock.fixed(now, ZoneOffset.UTC));
        String nickname = "N" + UUID.randomUUID().toString().substring(0, 8);
        service.updateMyProfile(new UpdateMyProfileCommand(first, nickname, null));
        // when / then
        assertThatThrownBy(() -> service.updateMyProfile(
                        new UpdateMyProfileCommand(second, nickname.toLowerCase(java.util.Locale.ROOT), null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.DUPLICATE_NICKNAME));
        assertThat(service.getMyProfile(second).completed()).isFalse();
        assertThat(service.updateMyProfile(new UpdateMyProfileCommand(first, nickname, "소개"))
                        .nickname())
                .isEqualTo(nickname);
    }

    @Test
    @DisplayName("없는 프로필과 미완성 공개 프로필은 같은 오류를 반환한다")
    void hidesMissingAndIncompleteProfiles() {
        // given
        FakeRepository repository = new FakeRepository();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberId missing = new MemberId(UUID.randomUUID());
        MemberId incomplete = new MemberId(UUID.randomUUID());
        repository.save(MemberProfile.create(incomplete, now), now);
        MemberProfileService service = new MemberProfileService(repository, Clock.fixed(now, ZoneOffset.UTC));
        // when / then
        for (Runnable action : new Runnable[] {
            () -> service.getMyProfile(missing),
            () -> service.getPublicProfile(missing),
            () -> service.getPublicProfile(incomplete),
            () -> service.updateMyProfile(new UpdateMyProfileCommand(missing, "별명12", null))
        }) {
            assertThatThrownBy(action::run)
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(MemberErrorCode.PROFILE_NOT_FOUND));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "admin1,소개,RESERVED_NICKNAME",
        "a!,소개,INVALID_NICKNAME",
        "abcdefghijklm,소개,INVALID_NICKNAME",
        "별명12,안녕,INVALID_BIO"
    })
    @DisplayName("서비스는 복원 경로로 우회하지 않고 사용자 입력 정책을 검사한다")
    void rejectsInvalidUserInput(String nickname, String bio, MemberErrorCode expected) {
        // given
        FakeRepository repository = new FakeRepository();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberId id = new MemberId(UUID.randomUUID());
        repository.save(MemberProfile.create(id, now), now);
        MemberProfileService service = new MemberProfileService(repository, Clock.fixed(now, ZoneOffset.UTC));
        String inputBio = expected == MemberErrorCode.INVALID_BIO ? bio.repeat(26) : bio;
        // when / then
        assertThatThrownBy(() -> service.updateMyProfile(new UpdateMyProfileCommand(id, nickname, inputBio)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(expected));
        assertThat(repository.profiles.get(id).nickname()).isNull();
        assertThat(repository.profiles.get(id).bio().value()).isNull();
    }

    @Test
    @DisplayName("현재 예약어인 닉네임을 복원한 회원도 본인 공개 조회와 새 닉네임 수정을 할 수 있다")
    void readsAndUpdatesRestoredReservedNickname() {
        // given
        FakeRepository repository = new FakeRepository();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MemberId id = new MemberId(UUID.randomUUID());
        repository.save(
                MemberProfile.reconstitute(
                        id, now.minusSeconds(60), Nickname.reconstitute("Admin1"), Bio.reconstitute("소개"), null, now),
                now);
        MemberProfileService service = new MemberProfileService(repository, Clock.fixed(now, ZoneOffset.UTC));
        // when / then
        assertThat(service.getMyProfile(id).nickname()).isEqualTo("Admin1");
        assertThat(service.getPublicProfile(id)).isEqualTo(new PublicProfileResult(id, "Admin1", "소개"));
        assertThat(service.updateMyProfile(new UpdateMyProfileCommand(id, "새별명", "새 소개"))
                        .nickname())
                .isEqualTo("새별명");
        assertThat(repository.profiles.get(id).bio().value()).isEqualTo("새 소개");
    }

    private static class FakeRepository implements MemberProfileRepository {
        private final Map<MemberId, MemberProfile> profiles = new HashMap<>();
        private final Map<MemberId, Instant> createdTimes = new HashMap<>();
        private int saveCount;
        private int lockedReadCount;

        @Override
        public Optional<MemberProfile> findById(MemberId memberId) {
            return Optional.ofNullable(profiles.get(memberId));
        }

        @Override
        public Optional<MemberProfile> findByIdForUpdate(MemberId memberId) {
            lockedReadCount++;
            return findById(memberId);
        }

        @Override
        public boolean existsByNicknameKeyAndMemberIdNot(String key, MemberId memberId) {
            return profiles.values().stream()
                    .anyMatch(profile -> !profile.memberId().equals(memberId)
                            && profile.nickname() != null
                            && profile.nickname().key().equals(key));
        }

        @Override
        public void update(MemberProfile profile) {
            profiles.put(profile.memberId(), profile);
        }

        @Override
        public boolean existsById(MemberId memberId) {
            return profiles.containsKey(memberId);
        }

        @Override
        public void save(MemberProfile profile, Instant createdAt) {
            profiles.put(profile.memberId(), profile);
            createdTimes.put(profile.memberId(), createdAt);
            saveCount++;
        }
    }
}
