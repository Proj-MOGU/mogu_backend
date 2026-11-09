package com.mogu.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.IssuedAccessToken;
import com.mogu.auth.application.IssuedRefreshToken;
import com.mogu.auth.application.RefreshOutcome;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenRedactionTest {
    @Test
    @DisplayName("요청과 응답과 결과의 문자열 표현에 토큰 원문을 노출하지 않는다")
    void redactsTokens() {
        // given
        String refresh = "refresh-secret";
        String access = "access-secret";
        var expires = Instant.parse("2026-12-01T00:00:00Z");
        var outcome = new RefreshOutcome.Rotated(
                new IssuedAccessToken(access, expires), new IssuedRefreshToken(refresh, expires));
        // when / then
        for (Object value : List.of(
                new RefreshRequest(refresh), new LogoutRequest(refresh), RefreshResponse.from(outcome), outcome))
            assertThat(value.toString()).doesNotContain(refresh, access);
    }
}
