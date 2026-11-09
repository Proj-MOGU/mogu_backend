package com.mogu.auth.api;

import com.mogu.auth.application.AccountInfoService;
import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내 정보")
@RestController
public class AccountInfoController {
    private final AccountInfoService service;

    public AccountInfoController(AccountInfoService service) {
        this.service = service;
    }

    @Operation(summary = "내 계정 정보 조회", description = "계정의 이름, 마스킹된 휴대폰 번호와 연결된 로그인 방식을 조회한다.")
    @ApiResponse(responseCode = "200", description = "내 계정 정보 조회 성공")
    @ApiResponse(
            responseCode = "401",
            description = "인증이 필요하거나 토큰이 유효하지 않음 (AUTH_UNAUTHORIZED)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/auth/me/account")
    public ResponseEntity<AccountInfoResponse> account(@Parameter(hidden = true) @LoginMember MemberId memberId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AccountInfoResponse.from(service.getAccount(memberId)));
    }
}
