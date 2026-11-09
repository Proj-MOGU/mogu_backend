package com.mogu.auth.application;

import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.RefreshTokenRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshService {
    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);
    private final RefreshTokenRepository tokens;
    private final AuthAccountRepository accounts;
    private final RefreshTokenIssuer refreshTokens;
    private final AccessTokenIssuer accessTokens;
    private final Clock clock;

    public RefreshService(
            RefreshTokenRepository tokens,
            AuthAccountRepository accounts,
            RefreshTokenIssuer refreshTokens,
            AccessTokenIssuer accessTokens,
            @Qualifier("authClock") Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    @Transactional
    public RefreshOutcome refresh(String refreshToken) {
        if (!RefreshTokenHasher.isValidFormat(refreshToken)) return new RefreshOutcome.Invalid();
        var hash = RefreshTokenHasher.hash(refreshToken);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (tokens.markUsedIfActive(hash, now)) {
            var token = tokens.findByTokenHash(hash).orElseThrow();
            var account = accounts.findByMemberId(token.memberId());
            if (account.isEmpty() || account.get().isSanctionedAt(now)) {
                tokens.revokeFamily(token.familyId(), now);
                return new RefreshOutcome.Invalid();
            }
            var issued = refreshTokens.issueInFamily(token.memberId(), token.familyId());
            return new RefreshOutcome.Rotated(accessTokens.issue(token.memberId()), issued);
        }
        var found = tokens.findByTokenHash(hash);
        if (found.isPresent()) {
            var token = found.get();
            if (token.isUsed() && !token.isRevoked()) {
                tokens.revokeFamily(token.familyId(), now);
                log.warn("Refresh Token 재사용 탐지 memberId={} familyId={}", token.memberId(), token.familyId());
            }
        }
        return new RefreshOutcome.Invalid();
    }
}
