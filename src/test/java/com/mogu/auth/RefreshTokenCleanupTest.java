package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.application.RefreshTokenCleanup;
import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.support.MutableAuthClock;
import com.mogu.auth.support.RefreshIntegrationSupport;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class RefreshTokenCleanupTest extends RefreshIntegrationSupport {
    private final RefreshTokenCleanup cleanup;

    @Autowired
    RefreshTokenCleanupTest(
            MockMvc mvc,
            JdbcClient jdbc,
            NonceService nonces,
            @Qualifier("authClock") Clock clock,
            JsonMapper json,
            RefreshTokenCleanup cleanup) {
        super(mvc, jdbc, nonces, clock, json);
        this.cleanup = cleanup;
    }

    @Test
    @DisplayName("만료 후 7일을 초과한 행만 삭제하고 정각과 직전 행은 보존한다")
    void deletesOnlyBeyondRetentionBoundary() throws Exception {
        // given
        account("cleanup", AccountStatus.ACTIVE, null);
        String old = raw(successfulLogin("cleanup"));
        var mutable = (MutableAuthClock) clock;
        mutable.advance(Duration.ofNanos(1000));
        String boundary = raw(successfulLogin("cleanup"));
        mutable.advance(Duration.ofNanos(1000));
        String recent = raw(successfulLogin("cleanup"));
        mutable.advance(Duration.ofDays(37).minusNanos(1000));
        // when
        cleanup.cleanup();
        // then
        assertThat(jdbc.sql("SELECT token_hash FROM auth_refresh_token")
                        .query(String.class)
                        .list())
                .containsExactlyInAnyOrder(hash(boundary), hash(recent))
                .doesNotContain(hash(old));
    }
}
