package com.mogu.auth.domain;

import java.time.Instant;

public interface UsedIdentityVerificationRepository {
    boolean markUsed(String verificationId, Instant now);
}
