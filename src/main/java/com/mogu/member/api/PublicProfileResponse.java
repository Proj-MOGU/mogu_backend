package com.mogu.member.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mogu.member.application.PublicProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicProfileResponse(
        @Schema(
                description = "회원 ID (UUID)",
                example = "0193a5b0-7c1e-7d2a-9f00-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID memberId,

        @Schema(
                description = "2~12자 한글·영문·숫자. 운영진 사칭 단어 불가. 대소문자 무시 중복 불가",
                example = "모구러버",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,

        @Schema(
                description = "최대 50자, 줄바꿈 불가. 비우면 없음",
                example = "동네 공동구매 자주 열어요",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String bio) {
    static PublicProfileResponse from(PublicProfileResult result) {
        return new PublicProfileResponse(result.memberId().value(), result.nickname(), result.bio());
    }
}
