package com.mogu.auth.domain;

import java.time.Instant;
import java.util.Objects;

public record LinkedSocialIdentity(SocialIdentity identity, Instant linkedAt, String email, boolean emailVerified) {
    public LinkedSocialIdentity(SocialIdentity identity, Instant linkedAt) {
        this(identity, linkedAt, null, false);
    }

    @Override
    public String toString() {
        return "LinkedSocialIdentity[personalInfo=***]";
    }

    public LinkedSocialIdentity {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(linkedAt);
    }
}
