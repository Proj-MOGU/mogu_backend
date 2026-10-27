package com.mogu.auth.application;

import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.SocialIdentity;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginCompletionService {
    private final SocialTokenVerificationService verification;
    private final AuthAccountRepository accounts;
    private final RegistrationTokenIssuer registrationTokens;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenIssuer refreshTokens;
    private final Clock clock;

    public LoginCompletionService(
            SocialTokenVerificationService verification,
            AuthAccountRepository accounts,
            RegistrationTokenIssuer registrationTokens,
            AccessTokenIssuer accessTokens,
            RefreshTokenIssuer refreshTokens,
            @Qualifier("authClock") Clock clock) {
        this.verification = verification;
        this.accounts = accounts;
        this.registrationTokens = registrationTokens;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional
    public LoginOutcome complete(VerifiedSocialIdentity identity) {
        verification.consumeNonce(identity);
        var social = new SocialIdentity(identity.provider(), identity.subject());
        var account = accounts.findBySocialIdentity(social);
        if (account.isEmpty()) return new LoginOutcome.RegistrationRequired(registrationTokens.issue(social));
        if (account.get().isSanctionedAt(clock.instant())) return new LoginOutcome.Sanctioned();
        var memberId = account.get().memberId();
        return new LoginOutcome.LoggedIn(accessTokens.issue(memberId), refreshTokens.issueNewFamily(memberId));
    }
}
