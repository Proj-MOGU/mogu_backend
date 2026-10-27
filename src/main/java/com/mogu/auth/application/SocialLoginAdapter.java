package com.mogu.auth.application;

import com.mogu.auth.domain.AuthProvider;

public interface SocialLoginAdapter {
    AuthProvider provider();

    VerifiedSocialIdentity verify(String idToken);
}
