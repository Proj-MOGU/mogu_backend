package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.support.IdentityIntegrationSupport;
import com.mogu.common.event.MemberRegistered;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@Import(RegistrationConcurrencyTest.EventConfig.class)
class RegistrationConcurrencyTest extends IdentityIntegrationSupport {
    private final EventCollector events;

    @Autowired
    RegistrationConcurrencyTest(
            MockMvc mvc,
            JdbcClient jdbc,
            NonceService nonces,
            @Qualifier("authClock") Clock clock,
            JsonMapper json,
            EventCollector events) {
        super(mvc, jdbc, nonces, clock, json);
        this.events = events;
    }

    @BeforeEach
    void clearEvents() {
        events.registered.clear();
    }

    private List<Integer> concurrently(String first, String firstId, String second, String secondId) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return complete(first, firstId).andReturn().getResponse().getStatus();
            });
            var two = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return complete(second, secondId).andReturn().getResponse().getStatus();
            });
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }

    @RepeatedTest(10)
    @DisplayName("동일 번호의 카카오와 구글 동시 가입은 회원 하나와 연결 두 개를 만든다")
    void serializesSameDi() throws Exception {
        // given
        String first = pending("kakao", "first");
        String second = pending("google", "second");
        String firstId = verification(first, PHONE_A);
        String secondId = verification(second, PHONE_A);
        // when
        var statuses = concurrently(first, firstId, second, secondId);
        // then
        assertThat(statuses).containsExactly(200, 200);
        assertThat(count("auth_account")).isEqualTo(1);
        assertThat(count("auth_social_identity")).isEqualTo(2);
        assertThat(count("auth_refresh_token")).isEqualTo(2);
        assertThat(events.registered).hasSize(1);
    }

    @RepeatedTest(10)
    @DisplayName("동일 가입 토큰의 서로 다른 인증 동시 제출은 하나만 가입시킨다")
    void serializesSameSocialIdentity() throws Exception {
        // given
        String pending = pending("kakao", "first");
        String firstId = verification(pending, PHONE_A);
        // 다른 DI라도 소셜 식별자 잠금으로 직렬화되어야 한다.
        String secondId = verification(pending, PHONE_B);
        // when
        var statuses = concurrently(pending, firstId, pending, secondId);
        // then
        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        assertThat(count("auth_account")).isEqualTo(1);
        assertThat(count("auth_social_identity")).isEqualTo(1);
        assertThat(count("auth_used_identity_verification")).isEqualTo(1);
        assertThat(events.registered).hasSize(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventConfig {
        @Bean
        EventCollector eventCollector() {
            return new EventCollector();
        }
    }

    static class EventCollector {
        final List<MemberRegistered> registered = new CopyOnWriteArrayList<>();

        @EventListener
        public void registered(MemberRegistered event) {
            registered.add(event);
        }
    }
}
