package com.mogu.auth.api;

import com.mogu.auth.application.NonceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증")
@RestController
public class NonceController {
    private final NonceService service;

    public NonceController(NonceService service) {
        this.service = service;
    }

    @Operation(summary = "로그인용 nonce 발급", description = "소셜 로그인 요청 전에 발급받아 ID Token의 nonce로 사용한다.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "nonce 발급 성공")
    @PostMapping("/api/auth/nonce")
    public ResponseEntity<NonceResponse> issue() {
        var issued = service.issue();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NonceResponse(issued.value(), issued.expiresAt()));
    }
}
