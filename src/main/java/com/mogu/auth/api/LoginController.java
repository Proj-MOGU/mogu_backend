package com.mogu.auth.api;

import com.mogu.auth.application.LoginService;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증")
@RestController
public class LoginController {
    private final LoginService service;

    public LoginController(LoginService service) {
        this.service = service;
    }

    @Operation(summary = "소셜 로그인", description = "응답의 registrationRequired가 true이면 본인인증으로 가입을 마쳐야 한다.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "로그인 성공 또는 가입 대기")
    @ApiResponse(
            responseCode = "400",
            description = "지원하지 않는 로그인 방식 (AUTH_UNSUPPORTED_PROVIDER); 입력값 오류 (COMMON_INVALID_INPUT)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "소셜 로그인 정보 확인 실패 (AUTH_INVALID_SOCIAL_TOKEN)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "이용이 제한된 계정 (AUTH_ACCOUNT_SANCTIONED)",
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
    @PostMapping("/api/auth/login/{provider}")
    public ResponseEntity<LoginResponse> login(
            @Parameter(
                            description = "소셜 로그인 제공자",
                            example = "kakao",
                            schema = @Schema(allowableValues = {"kakao", "google"}))
                    @PathVariable
                    String provider,
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(LoginResponse.from(service.login(AuthProvider.from(provider), request.idToken())));
    }
}
