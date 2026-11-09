package com.mogu.member.application;

import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.member.domain.Bio;
import com.mogu.member.domain.MemberErrorCode;
import com.mogu.member.domain.MemberProfile;
import com.mogu.member.domain.MemberProfileRepository;
import com.mogu.member.domain.Nickname;
import java.time.Clock;
import java.time.Instant;
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

    @Transactional(readOnly = true)
    public MyProfileResult getMyProfile(MemberId memberId) {
        return MyProfileResult.from(
                repository
                        .findById(memberId)
                        .orElseThrow(() -> new BusinessException(MemberErrorCode.PROFILE_NOT_FOUND)),
                clock.instant());
    }

    @Transactional
    public MyProfileResult updateMyProfile(UpdateMyProfileCommand command) {
        MemberProfile profile = repository
                .findByIdForUpdate(command.memberId())
                .orElseThrow(() -> new BusinessException(MemberErrorCode.PROFILE_NOT_FOUND));
        Nickname nickname = Nickname.of(command.nickname());
        Bio bio = Bio.of(command.bio());
        Instant now = clock.instant();
        if (repository.existsByNicknameKeyAndMemberIdNot(nickname.key(), command.memberId()))
            throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
        profile.updateProfile(nickname, bio, now);
        repository.update(profile);
        return MyProfileResult.from(profile, now);
    }

    @Transactional(readOnly = true)
    public PublicProfileResult getPublicProfile(MemberId memberId) {
        MemberProfile profile = repository
                .findById(memberId)
                .filter(MemberProfile::isCompleted)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.PROFILE_NOT_FOUND));
        return new PublicProfileResult(
                profile.memberId(), profile.nickname().value(), profile.bio().value());
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
