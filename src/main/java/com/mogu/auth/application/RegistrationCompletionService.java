package com.mogu.auth.application;

import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.DiHash;
import com.mogu.auth.domain.RegistrationLock;
import com.mogu.auth.domain.SanctionedDiRepository;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.auth.domain.UsedIdentityVerificationRepository;
import com.mogu.common.error.BusinessException;
import com.mogu.common.event.MemberRegistered;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationCompletionService {
    private final RegistrationLock registrationLock;
    private final UsedIdentityVerificationRepository used;
    private final AuthAccountRepository accounts;
    private final SanctionedDiRepository sanctions;
    private final MemberIdGenerator memberIds;
    private final ApplicationEventPublisher publisher;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenIssuer refreshTokens;
    private final Clock clock;

    public RegistrationCompletionService(
            RegistrationLock registrationLock,
            UsedIdentityVerificationRepository used,
            AuthAccountRepository accounts,
            SanctionedDiRepository sanctions,
            MemberIdGenerator memberIds,
            ApplicationEventPublisher publisher,
            AccessTokenIssuer accessTokens,
            RefreshTokenIssuer refreshTokens,
            @Qualifier("authClock") Clock clock) {
        this.registrationLock = registrationLock;
        this.used = used;
        this.accounts = accounts;
        this.sanctions = sanctions;
        this.memberIds = memberIds;
        this.publisher = publisher;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional
    public RegistrationOutcome complete(String verificationId, DiHash diHash, SocialIdentity social) {
        registrationLock.lock(diHash, social);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!used.markUsed(verificationId, now))
            throw new BusinessException(AuthErrorCode.INVALID_IDENTITY_VERIFICATION);
        if (accounts.findBySocialIdentity(social).isPresent()) throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        if (sanctions.isSanctioned(diHash, now)) return new RegistrationOutcome.Sanctioned();
        var existing = accounts.findByDiHash(diHash);
        if (existing.isPresent() && existing.get().isSanctionedAt(now)) return new RegistrationOutcome.Sanctioned();
        boolean newMember = existing.isEmpty();
        var account = existing.map(a -> a.link(social, now))
                .orElseGet(() -> AuthAccount.register(memberIds.generate(), diHash, social, now));
        accounts.save(account);
        if (newMember) publisher.publishEvent(new MemberRegistered(account.memberId(), now));
        return new RegistrationOutcome.Completed(
                accessTokens.issue(account.memberId()), refreshTokens.issueNewFamily(account.memberId()), newMember);
    }
}
