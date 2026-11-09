package com.mogu.member.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mogu.member.application.MyProfileResult;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyProfileResponse(
        UUID memberId,
        String nickname,
        String bio,
        Instant registeredAt,
        boolean completed,
        Instant nicknameChangeableAt) {
    static MyProfileResponse from(MyProfileResult result) {
        return new MyProfileResponse(
                result.memberId().value(),
                result.nickname(),
                result.bio(),
                result.registeredAt(),
                result.completed(),
                result.nicknameChangeableAt());
    }
}
