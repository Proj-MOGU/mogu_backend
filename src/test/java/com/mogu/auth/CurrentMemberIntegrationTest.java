package com.mogu.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.support.LoginIntegrationSupport;
import com.mogu.common.auth.CurrentMember;
import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@Import({CurrentMemberIntegrationTest.TestWebConfig.class, CurrentMemberIntegrationTest.CurrentMemberController.class})
class CurrentMemberIntegrationTest extends LoginIntegrationSupport {
    @Autowired
    CurrentMemberIntegrationTest(
            MockMvc mvc, JdbcClient jdbc, NonceService nonces, @Qualifier("authClock") Clock clock, JsonMapper json) {
        super(mvc, jdbc, nonces, clock, json);
    }

    @Test
    @DisplayName("로그인 토큰으로 인터셉터와 컨트롤러와 애노테이션에서 같은 회원을 조회한다")
    void findsSameMemberAcrossRequest() throws Exception {
        // given
        var subject = "current-" + UUID.randomUUID();
        var id = account(subject, AccountStatus.ACTIVE, null);
        var token = successfulLogin(subject).get("accessToken").asText();
        // when
        var result = mvc.perform(get("/test/current-member").header("Authorization", "Bearer " + token));
        // then
        result.andExpect(status().isOk())
                .andExpect(header().string("X-Test-Current-Member", id.toString()))
                .andExpect(jsonPath("$.currentMember").value(id.toString()))
                .andExpect(jsonPath("$.loginMember").value(id.toString()));
    }

    @Test
    @DisplayName("토큰 없이 공개 경로를 요청하면 인터셉터에서 회원이 없다")
    void returnsEmptyOnPublicPath() throws Exception {
        // given
        var request = post("/api/auth/nonce");
        // when
        var result = mvc.perform(request);
        // then
        result.andExpect(status().isOk()).andExpect(header().string("X-Test-Current-Member", "none"));
    }

    @Test
    @DisplayName("가입 대기 토큰으로 실제 본인인증 경로를 요청하면 인터셉터에서 회원이 없다")
    void returnsEmptyForPendingRegistration() throws Exception {
        // given
        var token = successfulLogin("pending-current-" + UUID.randomUUID())
                .get("registrationToken")
                .asText();
        var request = post("/api/auth/identity-verification")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{}");
        // when
        var result = mvc.perform(request);
        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(header().string("X-Test-Current-Member", "none"));
    }

    @Test
    @DisplayName("요청 스레드에서 인증되어도 다른 스레드에서는 회원이 없다")
    void returnsEmptyOnAnotherThread() throws Exception {
        // given
        var subject = "async-current-" + UUID.randomUUID();
        var id = account(subject, AccountStatus.ACTIVE, null);
        var token = successfulLogin(subject).get("accessToken").asText();
        // when
        var result = mvc.perform(get("/test/current-member/async").header("Authorization", "Bearer " + token));
        // then
        result.andExpect(status().isOk())
                .andExpect(header().string("X-Test-Current-Member", id.toString()))
                .andExpect(content().string("none"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestWebConfig implements WebMvcConfigurer {
        private final CurrentMember currentMember;

        TestWebConfig(CurrentMember currentMember) {
            this.currentMember = currentMember;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new CurrentMemberInterceptor(currentMember))
                    .addPathPatterns("/test/current-member/**", "/api/auth/nonce", "/api/auth/identity-verification");
        }
    }

    static class CurrentMemberInterceptor implements HandlerInterceptor {
        private final CurrentMember currentMember;

        CurrentMemberInterceptor(CurrentMember currentMember) {
            this.currentMember = currentMember;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            response.setHeader(
                    "X-Test-Current-Member",
                    currentMember.find().map(MemberId::toString).orElse("none"));
            return true;
        }
    }

    @RestController
    static class CurrentMemberController {
        private final CurrentMember currentMember;

        CurrentMemberController(CurrentMember currentMember) {
            this.currentMember = currentMember;
        }

        @GetMapping("/test/current-member")
        CurrentMemberResponse member(@LoginMember MemberId id) {
            return new CurrentMemberResponse(currentMember.find().orElseThrow().toString(), id.toString());
        }

        @GetMapping("/test/current-member/async")
        String async() throws Exception {
            return CompletableFuture.supplyAsync(() -> currentMember.find())
                    .get(5, TimeUnit.SECONDS)
                    .map(MemberId::toString)
                    .orElse("none");
        }
    }

    record CurrentMemberResponse(String currentMember, String loginMember) {}
}
