package com.mogu.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "ID Token을 입력해 주세요.")
        @Size(max = 8192, message = "ID Token이 너무 깁니다.")
        @Schema(description = "소셜 제공자의 ID Token", example = "eyJ...", requiredMode = Schema.RequiredMode.REQUIRED)
        String idToken) {
    @Override
    public String toString() {
        return "LoginRequest[idToken=***]";
    }
}
