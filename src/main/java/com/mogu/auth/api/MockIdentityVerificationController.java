package com.mogu.auth.api;

import com.mogu.auth.application.MockIdentityVerificationCreator;
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
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "본인인증")
@RestController
@Profile({"local", "test"})
public class MockIdentityVerificationController {
    private final MockIdentityVerificationCreator creator;

    public MockIdentityVerificationController(MockIdentityVerificationCreator creator) {
        this.creator = creator;
    }

    @Operation(summary = "Mock 본인인증 결과 생성", description = "local/test 프로파일 전용. 운영에는 없다. outcome을 생략하면 VERIFIED로 처리한다.")
    @SecurityRequirement(name = "registrationToken")
    @ApiResponse(responseCode = "200", description = "Mock 본인인증 결과 생성 성공")
    @ApiResponse(
            responseCode = "400",
            description = "입력값 오류 (COMMON_INVALID_INPUT)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "인증이 필요하거나 토큰이 유효하지 않음 (AUTH_UNAUTHORIZED)",
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
    @PostMapping("/api/auth/mock-identity-verifications")
    public ResponseEntity<MockIdentityVerificationResponse> create(
            @Parameter(hidden = true) @PendingRegistration PendingSocialIdentity pending,
            @Valid @RequestBody MockIdentityVerificationRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MockIdentityVerificationResponse(
                        creator.create(pending.registrationId(), request.phoneNumber(), request.outcome())));
    }
}
