package com.mogu.auth.application;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.common.error.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    private final SocialTokenVerificationService verification;
    private final LoginCompletionService completion;

    public LoginService(SocialTokenVerificationService verification, LoginCompletionService completion) {
        this.verification = verification;
        this.completion = completion;
    }

    public LoginResult login(AuthProvider provider, String idToken) {
        return switch (completion.complete(verification.verifyToken(provider, idToken))) {
            case LoginOutcome.LoggedIn result -> new LoginResult.LoggedIn(result.accessToken(), result.refreshToken());
            case LoginOutcome.RegistrationRequired result ->
                new LoginResult.RegistrationRequired(result.registrationToken());
            case LoginOutcome.Sanctioned ignored -> throw new BusinessException(AuthErrorCode.ACCOUNT_SANCTIONED);
        };
    }
}
