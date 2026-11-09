package com.mogu.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mogu.auth.application.PiiMasking;
import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.LinkedSocialIdentity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountInfoResponse(
        @Schema(
                description = "본인인증 이름. 저장된 값이 없으면 생략",
                example = "사용자",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String name,

        @Schema(
                description = "마스킹된 휴대폰 번호. 저장된 값이 없으면 생략",
                example = "010-****-5678",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String phoneNumber,

        @Schema(description = "연결된 소셜 로그인 방식 목록", example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<LoginMethod> loginMethods) {
    public AccountInfoResponse {
        loginMethods = List.copyOf(loginMethods);
    }

    public static AccountInfoResponse from(AuthAccount account) {
        return new AccountInfoResponse(
                account.name(),
                PiiMasking.phoneNumber(account.phoneNumber()),
                account.socialIdentities().stream()
                        .sorted(Comparator.comparing(LinkedSocialIdentity::linkedAt))
                        .map(i -> new LoginMethod(
                                i.identity().provider(),
                                PiiMasking.email(i.email()),
                                PiiMasking.email(i.email()) != null && i.emailVerified(),
                                i.linkedAt()))
                        .toList());
    }

    @Override
    public String toString() {
        return "AccountInfoResponse[personalInfo=***]";
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginMethod(
            @Schema(description = "소셜 로그인 제공자", example = "KAKAO", requiredMode = Schema.RequiredMode.REQUIRED)
            AuthProvider provider,

            @Schema(
                    description = "마스킹된 이메일. 저장된 값이 없으면 생략",
                    example = "u***@example.com",
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String email,

            @Schema(description = "소셜 제공자가 확인한 이메일 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean emailVerified,

            @Schema(
                    description = "소셜 로그인 연결 시각",
                    example = "2026-01-01T00:00:00Z",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            Instant linkedAt) {
        @Override
        public String toString() {
            return "LoginMethod[personalInfo=***]";
        }
    }
}
