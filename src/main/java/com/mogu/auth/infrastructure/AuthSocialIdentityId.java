package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AuthProvider;
import java.io.Serializable;
import java.util.Objects;

public class AuthSocialIdentityId implements Serializable {
    private AuthProvider provider;
    private String providerSubject;

    protected AuthSocialIdentityId() {}

    public AuthSocialIdentityId(AuthProvider provider, String providerSubject) {
        this.provider = provider;
        this.providerSubject = providerSubject;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuthSocialIdentityId id
                && provider == id.provider
                && Objects.equals(providerSubject, id.providerSubject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, providerSubject);
    }
}
