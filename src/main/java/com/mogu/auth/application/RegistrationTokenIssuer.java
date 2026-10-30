package com.mogu.auth.application;

import com.mogu.auth.domain.SocialIdentity;

public interface RegistrationTokenIssuer {
    IssuedRegistrationToken issue(SocialIdentity identity);
}
