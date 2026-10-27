package com.mogu.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "ID Token을 입력해 주세요.") @Size(max = 8192, message = "ID Token이 너무 깁니다.")
        String idToken) {
    @Override
    public String toString() {
        return "LoginRequest[idToken=***]";
    }
}
