package com.mogu.member.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateMyProfileRequest(
        @Schema(
                description = "2~12자 한글·영문·숫자. 운영진 사칭 단어 불가. 대소문자 무시 중복 불가",
                example = "모구러버",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "닉네임을 입력해 주세요.")
        String nickname,

        @Schema(
                description = "최대 50자, 줄바꿈 불가. 비우면 없음",
                example = "동네 공동구매 자주 열어요",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String bio) {}
