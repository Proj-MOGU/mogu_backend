package com.mogu.auth.api;

import com.mogu.auth.application.RefreshOutcome;
import com.mogu.auth.application.RefreshService;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.error.BusinessException;
import com.mogu.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증")
@RestController
public class RefreshController {
    private final RefreshService service;

    public RefreshController(RefreshService service) {
        this.service = service;
    }

    @Operation(summary = "로그인 토큰 갱신", description = "앱은 refresh 요청을 동시에 보내지 않는다. 이미 쓴 토큰을 다시 보내면 그 로그인 묶음 전체가 폐기된다.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "토큰 갱신 성공")
    @ApiResponse(
            responseCode = "400",
            description = "입력값 오류 (COMMON_INVALID_INPUT)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Refresh Token이 유효하지 않음 (AUTH_INVALID_REFRESH_TOKEN)",
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
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return switch (service.refresh(request.refreshToken())) {
            case RefreshOutcome.Rotated result ->
                ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(RefreshResponse.from(result));
            case RefreshOutcome.Invalid ignored -> throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        };
    }
}
