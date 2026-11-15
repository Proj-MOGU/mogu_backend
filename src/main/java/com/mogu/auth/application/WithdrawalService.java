package com.mogu.auth.application;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class WithdrawalService {
    private final WithdrawalAccountQuery accounts;
    private final SocialTokenVerificationService verification;
    private final WithdrawalCompletionService completion;

    public WithdrawalService(
            WithdrawalAccountQuery accounts,
            SocialTokenVerificationService verification,
            WithdrawalCompletionService completion) {
        this.accounts = accounts;
        this.verification = verification;
        this.completion = completion;
    }

    public void withdraw(MemberId memberId, AuthProvider provider, String idToken) {
        if (accounts.isWithdrawn(memberId)) return;
        try {
            var verified = verification.verifyToken(provider, idToken);
            completion.withdraw(memberId, verified);
        } catch (BusinessException exception) {
            if (exception.errorCode() == AuthErrorCode.INVALID_SOCIAL_TOKEN)
                throw new BusinessException(AuthErrorCode.REAUTHENTICATION_FAILED);
            throw exception;
        }
    }
}
