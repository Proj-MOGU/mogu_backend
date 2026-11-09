package com.mogu.member.application;

import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.MemberProfileRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberProfileService {
    private final MemberProfileRepository repository;
    private final Clock clock;

    public MemberProfileService(MemberProfileRepository repository, @Qualifier("memberClock") Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void createIncompleteProfile(CreateMemberProfileCommand command) {
        if (repository.existsById(command.memberId())) {
            return;
        }
        MemberProfile profile = MemberProfile.create(command.memberId(), command.registeredAt());
        repository.save(profile, clock.instant());
    }
}
