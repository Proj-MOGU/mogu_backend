package com.mogu.auth.infrastructure;

import com.mogu.auth.api.PendingSocialIdentity;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class PendingRegistrationAuthentication extends AbstractAuthenticationToken {
    private final PendingSocialIdentity identity;

    public PendingRegistrationAuthentication(PendingSocialIdentity identity) {
        super(List.of());
        this.identity = Objects.requireNonNull(identity);
        super.setAuthenticated(true);
    }

    @Override
    public PendingSocialIdentity getPrincipal() {
        return identity;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
