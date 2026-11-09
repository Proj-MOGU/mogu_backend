package com.mogu.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.RefreshToken;
import com.mogu.auth.domain.RefreshTokenRepository;
import com.mogu.common.auth.MemberId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshServiceTest {
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final AuthAccountRepository accounts = mock(AuthAccountRepository.class);
    private final RefreshTokenIssuer issuer = mock(RefreshTokenIssuer.class);
    private final AccessTokenIssuer access = mock(AccessTokenIssuer.class);
    private final Instant now = Instant.parse("2026-11-01T00:00:00.123456789Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final RefreshService service = new RefreshService(tokens, accounts, issuer, access, clock);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {" ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
            })
    @DisplayName("형식 오류는 저장소와 발급기를 호출하지 않는다")
    void invalidFormatDoesNotQuery(String raw) {
        // given / when
        var outcome = service.refresh(raw);
        new LogoutService(tokens, clock).logout(raw);
        // then
        assertThat(outcome).isInstanceOf(RefreshOutcome.Invalid.class);
        verifyNoInteractions(tokens, accounts, issuer, access);
    }

    @Test
    @DisplayName("계정이 없으면 패밀리를 폐기하고 결과로 실패를 반환한다")
    void missingAccountRevokesFamily() {
        // given
        String raw = "a".repeat(43);
        String hash = RefreshTokenHasher.hash(raw);
        Instant micros = now.truncatedTo(ChronoUnit.MICROS);
        var token = RefreshToken.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new MemberId(UUID.randomUUID()),
                hash,
                micros,
                Duration.ofDays(30));
        when(tokens.markUsedIfActive(hash, micros)).thenReturn(true);
        when(tokens.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(accounts.findByMemberId(token.memberId())).thenReturn(Optional.empty());
        // when
        var outcome = service.refresh(raw);
        // then
        assertThat(outcome).isInstanceOf(RefreshOutcome.Invalid.class);
        verify(tokens).revokeFamily(token.familyId(), micros);
        verifyNoInteractions(issuer, access);
    }

    @Test
    @DisplayName("정리 기준은 인증 시각의 마이크로초 정밀도로 7일 전이다")
    void cleanupUsesAuthClockAndMicroseconds() {
        // given / when
        new RefreshTokenCleanup(tokens, clock).cleanup();
        // then
        verify(tokens).deleteExpiredBefore(now.minus(Duration.ofDays(7)).truncatedTo(ChronoUnit.MICROS));
    }
}
