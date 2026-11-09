package com.mogu.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MockIdentityVerificationRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        @Pattern(regexp = "^01[016789]-?[0-9]{3,4}-?[0-9]{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
        @Schema(
                description = "휴대폰 번호 (숫자 10~11자리, 하이픈 허용). Mock 전용이므로 가짜 번호를 쓴다",
                example = "01000000000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String phoneNumber,

        @Pattern(regexp = "VERIFIED|FAILED", message = "인증 결과는 VERIFIED 또는 FAILED여야 합니다.")
        @Schema(
                description = "Mock 인증 결과. 생략하면 VERIFIED",
                example = "VERIFIED",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String outcome) {
    @Override
    public String toString() {
        return "MockIdentityVerificationRequest[phoneNumber=***, outcome=***]";
    }
}
