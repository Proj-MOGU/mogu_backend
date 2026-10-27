package com.mogu.auth.application;

import com.mogu.auth.domain.AuthProvider;

public record VerifiedSocialIdentity(AuthProvider provider, String subject, String nonce) {
    @Override
    public String toString() {
        return "VerifiedSocialIdentity[provider=" + provider + ", subject=" + subject + ", nonce=***]";
    }
}
