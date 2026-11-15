package com.mogu.auth.application;

import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.RefreshTokenRepository;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import com.mogu.common.event.MemberWithdrawn;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalCompletionService {
    private final AuthAccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final SocialTokenVerificationService verification;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public WithdrawalCompletionService(
            AuthAccountRepository accounts,
            RefreshTokenRepository refreshTokens,
            SocialTokenVerificationService verification,
            ApplicationEventPublisher publisher,
            @Qualifier("authClock") Clock clock) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.verification = verification;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public WithdrawalOutcome withdraw(MemberId memberId, VerifiedSocialIdentity reauth) {
        verification.consumeNonce(reauth);
        var account = accounts.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.UNAUTHORIZED));
        if (account.isWithdrawn()) return new WithdrawalOutcome.AlreadyWithdrawn();
        if (account.isSanctionedAt(clock.instant())) throw new BusinessException(AuthErrorCode.ACCOUNT_SANCTIONED);
        if (reauth == null
                || account.socialIdentities().stream()
                        .noneMatch(i -> i.identity().equals(new SocialIdentity(reauth.provider(), reauth.subject()))))
            throw new BusinessException(AuthErrorCode.REAUTHENTICATION_FAILED);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        accounts.save(account.withdraw(now));
        refreshTokens.revokeAllActiveByMember(memberId, now);
        publisher.publishEvent(new MemberWithdrawn(memberId, now));
        return new WithdrawalOutcome.Withdrawn();
    }
}
