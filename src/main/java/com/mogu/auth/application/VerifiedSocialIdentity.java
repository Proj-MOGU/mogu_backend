package com.mogu.auth.application;

import com.mogu.auth.domain.AuthProvider;

public record VerifiedSocialIdentity(
        AuthProvider provider, String subject, String nonce, String email, boolean emailVerified) {
    public VerifiedSocialIdentity(AuthProvider provider, String subject, String nonce) {
        this(provider, subject, nonce, null, false);
    }

    @Override
    public String toString() {
        return "VerifiedSocialIdentity[provider=" + provider + ", subject=" + subject + ", nonce=***]";
    }
}
