package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.AccessTokenIssuer;
import com.mogu.auth.application.NonceService;
import com.mogu.auth.support.LoginIntegrationSupport;
import com.mogu.common.auth.MemberId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

class RegistrationTokenSecurityTest extends LoginIntegrationSupport {
    private final AccessTokenIssuer access;
    private final JwtEncoder registrationEncoder;
    private final JwtEncoder accessEncoder;

    @Autowired
    RegistrationTokenSecurityTest(
            MockMvc mvc,
            JdbcClient jdbc,
            NonceService nonces,
            @Qualifier("authClock") Clock clock,
            JsonMapper json,
            AccessTokenIssuer access,
            @Qualifier("registrationJwtEncoder") JwtEncoder registrationEncoder,
            @Qualifier("jwtEncoder") JwtEncoder accessEncoder) {
        super(mvc, jdbc, nonces, clock, json);
        this.access = access;
        this.registrationEncoder = registrationEncoder;
        this.accessEncoder = accessEncoder;
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder()
                .issuer("mogu")
                .audience(List.of("mogu-registration"))
                .subject(UUID.randomUUID().toString())
                .issuedAt(clock.instant().minusSeconds(600))
                .expiresAt(clock.instant().plusSeconds(600))
                .id(UUID.randomUUID().toString())
                .claim("provider", "KAKAO")
                .claim("provider_sub", "subject");
    }

    private String encode(JwtEncoder encoder, String type, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.ES256).type(type).build(), claims))
                .getTokenValue();
    }

    private String unauthorized(ResultActions result) throws Exception {
        return result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("로그인에서 받은 가입 대기 토큰은 전용 경로에서 소셜 식별자를 주입한다")
    void loginRegistrationResolvesIdentity() throws Exception {
        // given
        String subject = "pending-" + UUID.randomUUID();
        var token = successfulLogin(subject).get("registrationToken").asText();
        // when / then
        mvc.perform(post("/api/auth/identity-verification").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("KAKAO"))
                .andExpect(jsonPath("$.providerSubject").value(subject))
                .andExpect(jsonPath("$.registrationId").isNotEmpty());
    }

    @Test
    @DisplayName("가입 대기 토큰과 Access Token의 사용 경로를 격리한다")
    void separatesTokenPurposes() throws Exception {
        // given
        var registration = successfulLogin("isolation-" + UUID.randomUUID())
                .get("registrationToken")
                .asText();
        var token = access.issue(new MemberId(UUID.randomUUID()));
        // when / then
        var normal =
                unauthorized(mvc.perform(get("/test/login-member").header("Authorization", "Bearer " + registration)));
        var pending = unauthorized(mvc.perform(
                post("/api/auth/identity-verification").header("Authorization", "Bearer " + token.value())));
        assertThat(normal).isEqualTo(pending);
        // 가입 대기 체인에서 인증되더라도 LoginMember 리졸버는 회원 ID를 반환하지 않는다.
        unauthorized(mvc.perform(
                post("/api/auth/mock-identity-verifications").header("Authorization", "Bearer " + registration)));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"absent", "expired", "accessTypeAndKey", "accessKey", "audience", "provider", "missingSubject"})
    @DisplayName("미인증 및 위조 토큰 일곱 경우를 각각 거부한다")
    void rejectsInvalidRegistrationTokens(String scenario) throws Exception {
        // given
        String token = switch (scenario) {
            case "absent" -> null;
            case "expired" ->
                encode(
                        registrationEncoder,
                        "registration+jwt",
                        claims().expiresAt(clock.instant().minusSeconds(61)).build());
            case "accessTypeAndKey" -> encode(accessEncoder, "at+jwt", claims().build());
            case "accessKey" -> encode(accessEncoder, "registration+jwt", claims().build());
            case "audience" ->
                encode(
                        registrationEncoder,
                        "registration+jwt",
                        claims().audience(List.of("other")).build());
            case "provider" ->
                encode(
                        registrationEncoder,
                        "registration+jwt",
                        claims().claim("provider", "APPLE").build());
            case "missingSubject" ->
                encode(
                        registrationEncoder,
                        "registration+jwt",
                        claims().claims(c -> c.remove("provider_sub")).build());
            default -> throw new IllegalArgumentException("Unknown scenario");
        };
        var request = post("/api/auth/identity-verification");
        if (token != null) request.header("Authorization", "Bearer " + token);
        // when
        var pending = unauthorized(mvc.perform(request));
        // then
        assertThat(pending).isEqualTo(unauthorized(mvc.perform(get("/test/login-member"))));
    }
}
