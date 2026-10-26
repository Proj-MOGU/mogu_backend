package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.AccessTokenIssuer;
import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(AccessTokenAuthenticationTest.DummyController.class)
class AccessTokenAuthenticationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    private final MockMvc mvc;
    private final AccessTokenIssuer issuer;

    @Autowired
    AccessTokenAuthenticationTest(MockMvc mvc, AccessTokenIssuer issuer) {
        this.mvc = mvc;
        this.issuer = issuer;
    }

    private MemberId member() {
        return MemberId.from("00000000-0000-7000-8000-000000000001");
    }

    @Test
    @DisplayName("유효한 토큰의 회원 ID를 컨트롤러에 전달한다")
    void validTokenResolvesMember() throws Exception {
        // given
        var token = issuer.issue(member());
        // when
        var result = mvc.perform(get("/test/me").header("Authorization", "Bearer " + token.value()));
        // then
        result.andExpect(status().isOk()).andExpect(content().string(member().toString()));
    }

    @Test
    @DisplayName("토큰 부재와 파싱 실패와 서명 실패는 같은 오류 본문을 반환한다")
    void allAuthenticationFailuresAreIdentical() throws Exception {
        // given
        var original = SignedJWT.parse(issuer.issue(member()).value());
        var forged = new SignedJWT(original.getHeader(), original.getJWTClaimsSet());
        forged.sign(new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate()));
        // when
        var absent = mvc.perform(get("/test/me"));
        var malformed = mvc.perform(get("/test/me").header("Authorization", "Bearer broken"));
        var wrong = mvc.perform(get("/test/me").header("Authorization", "Bearer " + forged.serialize()));
        // then
        assertThat(unauthorized(absent)).isEqualTo(unauthorized(malformed)).isEqualTo(unauthorized(wrong));
    }

    @Test
    @DisplayName("쿼리 파라미터에만 있는 토큰은 사용하지 않는다")
    void queryTokenIsRejected() throws Exception {
        // given
        var token = issuer.issue(member());
        // when
        var result = mvc.perform(get("/test/me").queryParam("access_token", token.value()));
        // then
        unauthorized(result);
    }

    @Test
    @DisplayName("폼 파라미터에만 있는 토큰은 사용하지 않는다")
    void formTokenIsRejected() throws Exception {
        // given
        var token = issuer.issue(member());
        // when
        var result = mvc.perform(post("/test/me")
                .contentType("application/x-www-form-urlencoded")
                .param("access_token", token.value()));
        // then
        unauthorized(result);
    }

    @Test
    @DisplayName("Basic 인증은 허용하지 않는다")
    void basicAuthenticationIsRejected() throws Exception {
        // given / when
        var result = mvc.perform(get("/test/me").header("Authorization", "Basic dGVzdDp0ZXN0"));
        // then
        unauthorized(result);
    }

    @Test
    @DisplayName("헬스 체크는 토큰 없이 접근할 수 있다")
    void healthIsPublic() throws Exception {
        // given / when
        var result = mvc.perform(get("/actuator/health"));
        // then
        result.andExpect(status().isOk());
    }

    private String unauthorized(ResultActions result) throws Exception {
        return result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @RestController
    static class DummyController {
        @GetMapping("/test/me")
        String me(@LoginMember MemberId memberId) {
            return memberId.toString();
        }
    }
}
