package com.mogu.auth.application;

import java.time.Instant;
import java.util.UUID;

public record VerifiedIdentity(
        String verificationId, UUID registrationId, String di, Instant verifiedAt, IdentitySource source) {
    @Override
    public String toString() {
        return "VerifiedIdentity[di=***]";
    }
}
