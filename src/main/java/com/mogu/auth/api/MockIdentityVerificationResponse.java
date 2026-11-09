package com.mogu.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record MockIdentityVerificationResponse(
        @Schema(
                description = "본인인증 결과 식별자",
                example = "verification-id-placeholder",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String verificationId) {}
