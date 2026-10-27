package com.mogu.auth.api;

import com.mogu.auth.application.NonceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NonceController {
    private final NonceService service;

    public NonceController(NonceService service) {
        this.service = service;
    }

    @PostMapping("/api/auth/nonce")
    public ResponseEntity<NonceResponse> issue() {
        var issued = service.issue();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NonceResponse(issued.value(), issued.expiresAt()));
    }
}
