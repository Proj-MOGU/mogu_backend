package com.mogu.member.api;

import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.member.application.MemberProfileService;
import com.mogu.member.application.UpdateMyProfileCommand;
import com.mogu.member.domain.MemberErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberProfileController {
    private final MemberProfileService service;

    public MemberProfileController(MemberProfileService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public MyProfileResponse getMyProfile(@LoginMember MemberId memberId) {
        return MyProfileResponse.from(service.getMyProfile(memberId));
    }

    @PutMapping("/me")
    public MyProfileResponse updateMyProfile(
            @LoginMember MemberId memberId, @Valid @RequestBody UpdateMyProfileRequest request) {
        return MyProfileResponse.from(
                service.updateMyProfile(new UpdateMyProfileCommand(memberId, request.nickname(), request.bio())));
    }

    @GetMapping("/{memberId}")
    public PublicProfileResponse getPublicProfile(@PathVariable String memberId) {
        MemberId id;
        try {
            id = MemberId.from(memberId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(MemberErrorCode.PROFILE_NOT_FOUND);
        }
        return PublicProfileResponse.from(service.getPublicProfile(id));
    }
}
