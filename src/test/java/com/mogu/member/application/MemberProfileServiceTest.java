package com.mogu.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mogu.common.auth.MemberId;
import com.mogu.common.event.MemberRegistered;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.MemberProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        MemberProfileRepository repository = new MemberProfileRepository() {
            @Override
            public boolean existsById(MemberId memberId) {
                return false;
            }

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

    private static class FakeRepository implements MemberProfileRepository {
        private final Map<MemberId, MemberProfile> profiles = new HashMap<>();
        private final Map<MemberId, Instant> createdTimes = new HashMap<>();
        private int saveCount;

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
