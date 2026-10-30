package com.mogu.auth.api;

import com.mogu.auth.domain.AuthProvider;
import java.util.Objects;
import java.util.UUID;

public record PendingSocialIdentity(UUID registrationId, AuthProvider provider, String providerSubject) {
    public PendingSocialIdentity {
        Objects.requireNonNull(registrationId);
        Objects.requireNonNull(provider);
        if (providerSubject == null || providerSubject.isBlank() || providerSubject.length() > 255)
            throw new IllegalArgumentException("Invalid pending identity");
    }
}
