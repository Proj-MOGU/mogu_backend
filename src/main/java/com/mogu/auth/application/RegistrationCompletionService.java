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
import java.util.Optional;
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
    private final WithdrawalPolicy withdrawal;

    public RegistrationCompletionService(
            RegistrationLock registrationLock,
            UsedIdentityVerificationRepository used,
            AuthAccountRepository accounts,
            SanctionedDiRepository sanctions,
            MemberIdGenerator memberIds,
            ApplicationEventPublisher publisher,
            AccessTokenIssuer accessTokens,
            RefreshTokenIssuer refreshTokens,
            @Qualifier("authClock") Clock clock,
            WithdrawalPolicy withdrawal) {
        this.registrationLock = registrationLock;
        this.used = used;
        this.accounts = accounts;
        this.sanctions = sanctions;
        this.memberIds = memberIds;
        this.publisher = publisher;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.withdrawal = withdrawal;
    }

    @Transactional
    public RegistrationOutcome complete(
            String verificationId,
            DiHash diHash,
            SocialIdentity social,
            String name,
            String phoneNumber,
            String email,
            boolean emailVerified) {
        registrationLock.lock(diHash, social);
        var usedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!used.markUsed(verificationId, usedAt))
            throw new BusinessException(AuthErrorCode.INVALID_IDENTITY_VERIFICATION);
        if (accounts.findBySocialIdentity(social).isPresent()) throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        var existing = accounts.findByDiHash(diHash).flatMap(a -> accounts.findByMemberIdForUpdate(a.memberId()));
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (sanctions.isSanctioned(diHash, now)) return new RegistrationOutcome.Sanctioned();
        if (existing.isPresent() && existing.get().isSanctionedAt(now)) return new RegistrationOutcome.Sanctioned();
        if (existing.isPresent() && existing.get().isWithdrawn()) {
            if (existing.get().isRejoinRestrictedAt(now, withdrawal.rejoinRestriction()))
                return new RegistrationOutcome.RejoinRestricted();
            accounts.save(existing.get().clearDiHash());
            existing = Optional.empty();
        }
        boolean newMember = existing.isEmpty();
        var account = existing.map(a -> a.link(social, now))
                .orElseGet(() -> AuthAccount.register(memberIds.generate(), diHash, social, now));
        account = account.updatePersonalInfo(name, phoneNumber).updateSocialEmail(social, email, emailVerified);
        accounts.save(account);
        if (newMember) publisher.publishEvent(new MemberRegistered(account.memberId(), now));
        return new RegistrationOutcome.Completed(
                accessTokens.issue(account.memberId()), refreshTokens.issueNewFamily(account.memberId()), newMember);
    }
}
