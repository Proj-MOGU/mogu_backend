package com.mogu.auth.api;

import com.mogu.auth.application.WithdrawalService;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증")
@RestController
public class WithdrawalController {
    private final WithdrawalService service;

    public WithdrawalController(WithdrawalService service) {
        this.service = service;
    }

    @Operation(
            summary = "회원 탈퇴",
            description =
                    "앱은 POST /api/auth/nonce로 받은 nonce로 소셜 로그인을 다시 해 ID Token을 보낸다. 같은 회원에 연결된 소셜 계정이어야 한다. 이미 탈퇴했으면"
                            + " 204.")
    @ApiResponse(responseCode = "204", description = "탈퇴 처리 완료 또는 이미 탈퇴한 회원", content = @Content)
    @ApiResponse(
            responseCode = "400",
            description = "지원하지 않는 로그인 방식 (AUTH_UNSUPPORTED_PROVIDER); 입력값 오류 (COMMON_INVALID_INPUT)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "인증이 필요하거나 Access Token이 유효하지 않음 (AUTH_UNAUTHORIZED)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "소셜 재인증 실패 (AUTH_REAUTHENTICATION_FAILED); 이용이 제한된 계정 (AUTH_ACCOUNT_SANCTIONED)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description = "소셜 로그인 서비스 일시 장애 (AUTH_PROVIDER_UNAVAILABLE)",
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
    @PostMapping("/api/auth/withdrawal")
    public ResponseEntity<Void> withdraw(
            @Parameter(hidden = true) @LoginMember MemberId memberId, @Valid @RequestBody WithdrawalRequest request) {
        service.withdraw(memberId, AuthProvider.from(request.provider()), request.idToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
