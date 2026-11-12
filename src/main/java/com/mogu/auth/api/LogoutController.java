package com.mogu.auth.api;

import com.mogu.auth.application.LogoutService;
import com.mogu.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증")
@RestController
public class LogoutController {
    private final LogoutService service;

    public LogoutController(LogoutService service) {
        this.service = service;
    }

    @Operation(summary = "로그아웃", description = "로그인 묶음 전체를 폐기한다. 토큰이나 본문이 없거나 잘못되어도 요청 수 제한에 걸리지 않으면 204를 반환한다.")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "로그아웃 처리 완료", content = @Content)
    @ApiResponse(
            responseCode = "429",
            description = "요청이 너무 많음 (AUTH_TOO_MANY_REQUESTS)",
            headers =
                    @Header(
                            name = "Retry-After",
                            description = "재시도까지 대기할 초 (정수, 올림)",
                            schema = @Schema(type = "integer")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        service.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> malformedBody() {
        return ResponseEntity.noContent().build();
    }
}
