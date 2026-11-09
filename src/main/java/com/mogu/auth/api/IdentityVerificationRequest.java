package com.mogu.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IdentityVerificationRequest(
        @NotBlank(message = "본인인증 식별자를 입력해 주세요.") @Size(max = 64, message = "본인인증 식별자는 64자 이하여야 합니다.")
        String verificationId) {}
