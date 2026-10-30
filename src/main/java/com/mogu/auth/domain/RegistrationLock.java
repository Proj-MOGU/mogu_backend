package com.mogu.auth.domain;

public interface RegistrationLock {
    void lock(DiHash diHash, SocialIdentity identity);
}
