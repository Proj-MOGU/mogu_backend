package com.mogu.auth.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.LinkedSocialIdentity;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(LoginIntegrationSupport.DummyController.class)
public abstract class LoginIntegrationSupport {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    protected static FakeOidcProvider fake;
    protected static FakeOidcProvider google;

    @org.junit.jupiter.api.BeforeAll
    static void startProvider() {
        fake = new FakeOidcProvider();
        google = new FakeOidcProvider();
    }

    @org.junit.jupiter.api.AfterAll
    static void stopProvider() {
        if (fake != null) fake.close();
        if (google != null) google.close();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "mogu.auth.social.kakao.jwk-set-uri", () -> fake.jwkSetUri().toString());
        registry.add("mogu.auth.social.kakao.issuers", () -> "https://kauth.kakao.com");
        registry.add("mogu.auth.social.kakao.audiences", () -> "test-client");
        registry.add("mogu.auth.social.kakao.read-timeout", () -> "10s");
        registry.add(
                "mogu.auth.social.google.jwk-set-uri", () -> google.jwkSetUri().toString());
        registry.add("mogu.auth.social.google.audiences", () -> "test-client");
    }

    protected final MockMvc mvc;
    protected final JdbcClient jdbc;
    protected final NonceService nonces;
    protected final Clock clock;
    protected final JsonMapper json;

    protected LoginIntegrationSupport(MockMvc mvc, JdbcClient jdbc, NonceService nonces, Clock clock, JsonMapper json) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.nonces = nonces;
        this.clock = clock;
        this.json = json;
    }

    protected String token(String subject) throws Exception {
        return token("kakao", subject);
    }

    protected String token(String provider, String subject) throws Exception {
        var now = clock.instant();
        return (provider.equals("kakao") ? fake : google)
                .sign(new JWTClaimsSet.Builder()
                        .issuer(provider.equals("kakao") ? "https://kauth.kakao.com" : "https://accounts.google.com")
                        .audience("test-client")
                        .subject(subject)
                        .claim("nonce", nonces.issue().value())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(300)))
                        .build());
    }

    protected ResultActions login(String provider, String token) throws Exception {
        return mvc.perform(post("/api/auth/login/" + provider)
                .contentType("application/json")
                .content(json.writeValueAsString(new com.mogu.auth.api.LoginRequest(token))));
    }

    protected JsonNode successfulLogin(String subject) throws Exception {
        return json.readTree(login("kakao", token(subject))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    protected MemberId account(String subject, AccountStatus status, Instant until) {
        var id = new MemberId(UUID.randomUUID());
        AuthAccountFixtures.insert(
                jdbc,
                AuthAccount.reconstitute(
                        id,
                        new com.mogu.auth.domain.DiHash(
                                id.value().toString().replace("-", "").repeat(2)),
                        status,
                        until,
                        clock.instant(),
                        List.of(new LinkedSocialIdentity(
                                new SocialIdentity(AuthProvider.KAKAO, subject), clock.instant()))));
        return id;
    }

    @RestController
    public static class DummyController {
        @GetMapping("/test/login-member")
        public String member(@LoginMember MemberId id) {
            return id.toString();
        }
    }
}
