package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.support.RefreshIntegrationSupport;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class RefreshConcurrencyTest extends RefreshIntegrationSupport {
    @Autowired
    RefreshConcurrencyTest(
            MockMvc mvc, JdbcClient jdbc, NonceService nonces, @Qualifier("authClock") Clock clock, JsonMapper json) {
        super(mvc, jdbc, nonces, clock, json);
    }

    /** 동시 교체는 재사용으로 탐지되어 성공 응답의 토큰까지 폐기되므로 앱은 refresh 요청을 직렬화해야 한다. */
    @RepeatedTest(10)
    @DisplayName("동시 교체는 한 번만 성공하고 성공 응답의 새 토큰까지 폐기한다")
    void concurrentRefreshRevokesEntireFamily() throws Exception {
        // given
        account("race", AccountStatus.ACTIVE, null);
        String raw = raw(successfulLogin("race"));
        var family = family(raw);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MockHttpServletResponse> request = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return refresh(raw).andReturn().getResponse();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            // when
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            // then
            assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 401);
            assertRevoked(family, true);
            assertThat(jdbc.sql("SELECT count(*) FROM auth_refresh_token WHERE family_id = :family")
                            .param("family", family)
                            .query(Long.class)
                            .single())
                    .isEqualTo(2);
            var success = responses.stream()
                    .filter(r -> r.getStatus() == 200)
                    .findFirst()
                    .orElseThrow();
            invalid(raw(json.readTree(success.getContentAsString())));
        }
    }
}
