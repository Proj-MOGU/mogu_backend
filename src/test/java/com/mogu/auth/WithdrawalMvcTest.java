package com.mogu.auth;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.api.WithdrawalController;
import com.mogu.auth.api.WithdrawalRequest;
import com.mogu.auth.application.AccessTokenIssuer;
import com.mogu.auth.application.RegistrationTokenIssuer;
import com.mogu.auth.application.WithdrawalService;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.SocialIdentity;
import com.mogu.auth.infrastructure.AuthWebConfig;
import com.mogu.auth.infrastructure.JsonAuthenticationEntryPoint;
import com.mogu.auth.infrastructure.JwtAccessTokenIssuer;
import com.mogu.auth.infrastructure.JwtKeyConfig;
import com.mogu.auth.infrastructure.JwtRegistrationTokenIssuer;
import com.mogu.auth.infrastructure.MemberAuthenticationConverter;
import com.mogu.auth.infrastructure.PendingRegistrationAuthenticationConverter;
import com.mogu.auth.infrastructure.PiiKeyConfig;
import com.mogu.auth.infrastructure.RegistrationKeyConfig;
import com.mogu.auth.infrastructure.SecurityConfig;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(WithdrawalController.class)
@ActiveProfiles("test")
@Import({
    SecurityConfig.class,
    PiiKeyConfig.class,
    RegistrationKeyConfig.class,
    PendingRegistrationAuthenticationConverter.class,
    JwtRegistrationTokenIssuer.class,
    JwtKeyConfig.class,
    JwtAccessTokenIssuer.class,
    MemberAuthenticationConverter.class,
    JsonAuthenticationEntryPoint.class,
    AuthWebConfig.class
})
class WithdrawalMvcTest {
    @MockitoBean
    private WithdrawalService service;

    private final MockMvc mvc;
    private final AccessTokenIssuer tokens;
    private final RegistrationTokenIssuer registrations;

    @Autowired
    WithdrawalMvcTest(MockMvc mvc, AccessTokenIssuer tokens, RegistrationTokenIssuer registrations) {
        this.mvc = mvc;
        this.tokens = tokens;
        this.registrations = registrations;
    }

    @Test
    @DisplayName("인증 회원의 탈퇴는 204와 캐시 금지를 반환한다")
    void withdrawsAuthenticatedMember() throws Exception {
        // given
        var id = new MemberId(UUID.randomUUID());
        // when / then
        withdraw(tokens.issue(id).value(), "kakao", "eyJ...")
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).withdraw(id, AuthProvider.KAKAO, "eyJ...");
    }

    @Test
    @DisplayName("Access Token 없음과 위조 토큰 및 가입 대기 토큰은 401로 거절한다")
    void rejectsWrongTokens() throws Exception {
        // given
        String pending = registrations
                .issue(new SocialIdentity(AuthProvider.KAKAO, "subject"))
                .value();
        // when / then
        for (String token : new String[] {null, "forged", pending}) {
            withdraw(token, "kakao", "eyJ...")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("미지원 공급자와 빈 값 및 너무 긴 ID Token은 400으로 거절한다")
    void validatesInput() throws Exception {
        // given
        String access = tokens.issue(new MemberId(UUID.randomUUID())).value();
        // when / then
        for (String provider : new String[] {"apple", null, ""})
            withdraw(access, provider, "token")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AUTH_UNSUPPORTED_PROVIDER"));
        for (String token : new String[] {null, "", " ", "a".repeat(8193)})
            withdraw(access, "kakao", token)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"REAUTHENTICATION_FAILED,403", "ACCOUNT_SANCTIONED,403", "PROVIDER_UNAVAILABLE,503", "UNAUTHORIZED,401"
    })
    @DisplayName("재인증과 제재 및 공급자 장애의 상태 코드와 오류 코드를 그대로 응답한다")
    void returnsServiceErrors(AuthErrorCode error, int statusCode) throws Exception {
        // given
        var id = new MemberId(UUID.randomUUID());
        doThrow(new BusinessException(error)).when(service).withdraw(id, AuthProvider.KAKAO, "token");
        // when / then
        withdraw(tokens.issue(id).value(), "kakao", "token")
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(error.code()));
    }

    private ResultActions withdraw(String access, String provider, String token) throws Exception {
        var request = post("/api/auth/withdrawal")
                .contentType("application/json")
                .content(new JsonMapper().writeValueAsString(new WithdrawalRequest(provider, token)));
        if (access != null) request.header("Authorization", "Bearer " + access);
        return mvc.perform(request);
    }
}
