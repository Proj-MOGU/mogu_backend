package com.mogu.auth.api;

import com.mogu.auth.domain.AuthProvider;
import java.util.Objects;
import java.util.UUID;

public record PendingSocialIdentity(
        UUID registrationId, AuthProvider provider, String providerSubject, String email, boolean emailVerified) {
    public PendingSocialIdentity(UUID registrationId, AuthProvider provider, String providerSubject) {
        this(registrationId, provider, providerSubject, null, false);
    }

    @Override
    public String toString() {
        return "PendingSocialIdentity[personalInfo=***]";
    }

    public PendingSocialIdentity {
        Objects.requireNonNull(registrationId);
        Objects.requireNonNull(provider);
        if (providerSubject == null || providerSubject.isBlank() || providerSubject.length() > 255)
            throw new IllegalArgumentException("Invalid pending identity");
    }
}
