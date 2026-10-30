package com.mogu.auth.api;

import com.mogu.auth.application.MockIdentityVerificationCreator;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test"})
public class MockIdentityVerificationController {
    private final MockIdentityVerificationCreator creator;

    public MockIdentityVerificationController(MockIdentityVerificationCreator creator) {
        this.creator = creator;
    }

    @PostMapping("/api/auth/mock-identity-verifications")
    public ResponseEntity<MockIdentityVerificationResponse> create(
            @PendingRegistration PendingSocialIdentity pending,
            @Valid @RequestBody MockIdentityVerificationRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MockIdentityVerificationResponse(
                        creator.create(pending.registrationId(), request.phoneNumber(), request.outcome())));
    }
}
