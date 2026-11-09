package com.mogu.auth.application;

import com.mogu.auth.domain.RefreshTokenRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {
    private final RefreshTokenRepository tokens;
    private final Clock clock;

    public LogoutService(RefreshTokenRepository tokens, @Qualifier("authClock") Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public void logout(String refreshToken) {
        if (!RefreshTokenHasher.isValidFormat(refreshToken)) return;
        tokens.findByTokenHash(RefreshTokenHasher.hash(refreshToken))
                .ifPresent(token ->
                        tokens.revokeFamily(token.familyId(), clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
}
