package com.mogu.auth.api;

import com.mogu.auth.application.IdentityVerificationService;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "본인인증")
@RestController
public class IdentityVerificationController {
    private final IdentityVerificationService service;

    public IdentityVerificationController(IdentityVerificationService service) {
        this.service = service;
    }

    @Operation(
            summary = "본인인증으로 가입 완료",
            description = "가입 대기 토큰과 연결된 본인인증 결과로 가입하거나 기존 계정에 로그인 방식을 연결한다. newMember가 true이면 프로필 입력 화면으로 이동한다.")
    @SecurityRequirement(name = "registrationToken")
    @ApiResponse(responseCode = "200", description = "본인인증 및 로그인 성공")
    @ApiResponse(
            responseCode = "400",
            description = "입력값 오류 (COMMON_INVALID_INPUT); 본인인증 정보 확인 실패 (AUTH_INVALID_IDENTITY_VERIFICATION)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "인증이 필요하거나 토큰이 유효하지 않음 (AUTH_UNAUTHORIZED)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "이용이 제한된 계정 (AUTH_ACCOUNT_SANCTIONED)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "409",
            description = "같은 로그인 방식의 다른 계정이 이미 연결됨 (AUTH_PROVIDER_ALREADY_LINKED)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "429",
            description = "요청이 너무 많음 (AUTH_TOO_MANY_REQUESTS)",
            headers =
                    @Header(
                            name = "Retry-After",
                            description = "재시도까지 대기할 초 (정수, 올림)",
                            schema = @Schema(type = "integer")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/auth/identity-verification")
    public ResponseEntity<IdentityVerificationResponse> verify(
            @Parameter(hidden = true) @PendingRegistration PendingSocialIdentity pending,
            @Valid @RequestBody IdentityVerificationRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(IdentityVerificationResponse.from(service.verify(
                        pending.registrationId(),
                        new SocialIdentity(pending.provider(), pending.providerSubject()),
                        request.verificationId(),
                        pending.email(),
                        pending.emailVerified())));
    }
}
