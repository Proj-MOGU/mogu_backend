package com.mogu.auth.application;

import java.util.UUID;

public interface MockIdentityVerificationCreator {
    String create(UUID registrationId, String phoneNumber, String outcome);
}
