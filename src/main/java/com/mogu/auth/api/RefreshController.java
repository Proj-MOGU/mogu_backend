package com.mogu.auth.api;

import com.mogu.auth.application.RefreshOutcome;
import com.mogu.auth.application.RefreshService;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.error.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RefreshController {
    private final RefreshService service;

    public RefreshController(RefreshService service) {
        this.service = service;
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return switch (service.refresh(request.refreshToken())) {
            case RefreshOutcome.Rotated result ->
                ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(RefreshResponse.from(result));
            case RefreshOutcome.Invalid ignored -> throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        };
    }
}
