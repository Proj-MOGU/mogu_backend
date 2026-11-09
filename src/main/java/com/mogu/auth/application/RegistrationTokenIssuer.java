package com.mogu.auth.application;

import com.mogu.auth.domain.SocialIdentity;

public interface RegistrationTokenIssuer {
    default IssuedRegistrationToken issue(SocialIdentity identity) {
        return issue(identity, null, false);
    }

    IssuedRegistrationToken issue(SocialIdentity identity, String email, boolean emailVerified);
}
