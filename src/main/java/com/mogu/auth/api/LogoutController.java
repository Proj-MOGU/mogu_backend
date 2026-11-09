package com.mogu.auth.api;

import com.mogu.auth.application.LogoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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

    @Operation(summary = "로그아웃", description = "로그인 묶음 전체를 폐기한다. 토큰이나 본문이 없거나 잘못되어도 응답은 항상 204이다.")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "로그아웃 처리 완료", content = @Content)
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
