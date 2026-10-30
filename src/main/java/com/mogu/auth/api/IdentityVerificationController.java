package com.mogu.auth.api;

import com.mogu.auth.application.IdentityVerificationService;
import com.mogu.auth.domain.SocialIdentity;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityVerificationController {
    private final IdentityVerificationService service;

    public IdentityVerificationController(IdentityVerificationService service) {
        this.service = service;
    }

    @PostMapping("/api/auth/identity-verification")
    public ResponseEntity<IdentityVerificationResponse> verify(
            @PendingRegistration PendingSocialIdentity pending,
            @Valid @RequestBody IdentityVerificationRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(IdentityVerificationResponse.from(service.verify(
                        pending.registrationId(),
                        new SocialIdentity(pending.provider(), pending.providerSubject()),
                        request.verificationId())));
    }
}
