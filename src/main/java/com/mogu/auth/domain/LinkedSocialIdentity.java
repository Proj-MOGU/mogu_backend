package com.mogu.auth.domain;

import java.time.Instant;
import java.util.Objects;

public record LinkedSocialIdentity(SocialIdentity identity, Instant linkedAt) {
    public LinkedSocialIdentity {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(linkedAt);
    }
}
