package com.mogu.member.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateMyProfileRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.") String nickname, String bio) {}
