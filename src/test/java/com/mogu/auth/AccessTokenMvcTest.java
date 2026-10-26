package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.AccessTokenIssuer;
import com.mogu.auth.infrastructure.AuthWebConfig;
import com.mogu.auth.infrastructure.JsonAuthenticationEntryPoint;
import com.mogu.auth.infrastructure.JwtAccessTokenIssuer;
import com.mogu.auth.infrastructure.JwtKeyConfig;
import com.mogu.auth.infrastructure.MemberAuthenticationConverter;
import com.mogu.auth.infrastructure.SecurityConfig;
import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(AccessTokenMvcTest.DummyController.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "logging.level.com.mogu.auth.infrastructure.JsonAuthenticationEntryPoint=DEBUG")
@Import({
    AccessTokenMvcTest.DummyController.class,
    SecurityConfig.class,
    JwtKeyConfig.class,
    JwtAccessTokenIssuer.class,
    MemberAuthenticationConverter.class,
    JsonAuthenticationEntryPoint.class,
    AuthWebConfig.class
})
class AccessTokenMvcTest {
    private final MockMvc mvc;
    private final AccessTokenIssuer issuer;
    private final JsonAuthenticationEntryPoint entryPoint;

    @Autowired
    AccessTokenMvcTest(MockMvc mvc, AccessTokenIssuer issuer, JsonAuthenticationEntryPoint entryPoint) {
        this.mvc = mvc;
        this.issuer = issuer;
        this.entryPoint = entryPoint;
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
    @DisplayName("인증 실패 로그는 예외 종류와 이유를 남기고 인증 헤더와 토큰은 숨긴다")
    @ExtendWith(OutputCaptureExtension.class)
    void authenticationLogsRedactCredentials(CapturedOutput output) throws Exception {
        // given
        String token = issuer.issue(member()).value();
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        var failure = new BadCredentialsException("Invalid credential: Bearer " + token);
        // when
        entryPoint.commence(request, response, failure);
        // then
        assertThat(output.getAll())
                .contains("DEBUG", "BadCredentialsException", "Invalid credential", "***")
                .doesNotContain(token, "Bearer " + token);
        assertThat(response.getStatus()).isEqualTo(401);
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
