package com.mogu.auth.application;

public interface IdentityVerificationAdapter {
    VerifiedIdentity fetch(String verificationId);
}
