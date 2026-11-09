package com.mogu.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MockIdentityVerificationRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        @Pattern(regexp = "^01[016789][0-9]{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
        String phoneNumber,

        @Pattern(regexp = "VERIFIED|FAILED", message = "인증 결과는 VERIFIED 또는 FAILED여야 합니다.")
        String outcome) {
    @Override
    public String toString() {
        return "MockIdentityVerificationRequest[phoneNumber=***, outcome=***]";
    }
}
