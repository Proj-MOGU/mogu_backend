package com.mogu.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @NotBlank(message = "Refresh Token을 입력해 주세요.") @Size(max = 128, message = "Refresh Token은 128자 이하여야 합니다.")
        String refreshToken) {
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=***]";
    }
}
