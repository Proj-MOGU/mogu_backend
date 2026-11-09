package com.mogu.member.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mogu.member.application.PublicProfileResult;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicProfileResponse(UUID memberId, String nickname, String bio) {
    static PublicProfileResponse from(PublicProfileResult result) {
        return new PublicProfileResponse(result.memberId().value(), result.nickname(), result.bio());
    }
}
