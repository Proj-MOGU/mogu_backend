package com.mogu.auth.domain;

import java.util.Objects;

public record SocialIdentity(AuthProvider provider, String subject) {
    public SocialIdentity {
        Objects.requireNonNull(provider, "provider is required");
        if (subject == null || subject.isBlank() || subject.length() > 255)
            throw new IllegalArgumentException("Invalid social subject");
    }
}
