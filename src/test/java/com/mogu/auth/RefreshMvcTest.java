package com.mogu.auth;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.api.LogoutController;
import com.mogu.auth.api.RefreshController;
import com.mogu.auth.application.IssuedAccessToken;
import com.mogu.auth.application.IssuedRefreshToken;
import com.mogu.auth.application.LogoutService;
import com.mogu.auth.application.RefreshOutcome;
import com.mogu.auth.application.RefreshService;
import com.mogu.auth.infrastructure.JsonAuthenticationEntryPoint;
import com.mogu.auth.infrastructure.JwtKeyConfig;
import com.mogu.auth.infrastructure.MemberAuthenticationConverter;
import com.mogu.auth.infrastructure.PendingRegistrationAuthenticationConverter;
import com.mogu.auth.infrastructure.RegistrationKeyConfig;
import com.mogu.auth.infrastructure.SecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({RefreshController.class, LogoutController.class})
@ActiveProfiles("test")
@Import({
    SecurityConfig.class,
    RegistrationKeyConfig.class,
    PendingRegistrationAuthenticationConverter.class,
    JwtKeyConfig.class,
    MemberAuthenticationConverter.class,
    JsonAuthenticationEntryPoint.class
})
@MockitoBean(types = {RefreshService.class, LogoutService.class})
class RefreshMvcTest {
    private final MockMvc mvc;
    private final RefreshService refresh;
    private final LogoutService logout;

    @Autowired
    RefreshMvcTest(MockMvc mvc, RefreshService refresh, LogoutService logout) {
        this.mvc = mvc;
        this.refresh = refresh;
        this.logout = logout;
    }

    @Test
    @DisplayName("교체 결과는 인증 없이 200과 캐시 금지 헤더로 반환한다")
    void returnsRotationWithoutAuthentication() throws Exception {
        // given
        var expiry = Instant.parse("2026-12-01T00:00:00Z");
        when(refresh.refresh("input"))
                .thenReturn(new RefreshOutcome.Rotated(
                        new IssuedAccessToken("access", expiry), new IssuedRefreshToken("refresh", expiry)));
        // when / then
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"input\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.accessTokenExpiresAt").value(expiry.toString()))
                .andExpect(jsonPath("$.refreshTokenExpiresAt").value(expiry.toString()));
    }

    @Test
    @DisplayName("서비스의 실패 결과는 고정된 401 응답으로 변환한다")
    void invalidOutcomeBecomesUnauthorized() throws Exception {
        // given
        when(refresh.refresh("invalid")).thenReturn(new RefreshOutcome.Invalid());
        // when / then
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"AUTH_INVALID_REFRESH_TOKEN\",\"message\":\"다시 로그인해 주세요.\"}"));
    }

    @Test
    @DisplayName("빈 값과 초과 길이는 서비스 호출 전에 400으로 거부한다")
    void validationRejectsBeforeService() throws Exception {
        // given
        var bodies = List.of(
                "{}",
                "{\"refreshToken\":null}",
                "{\"refreshToken\":\"\"}",
                "{\"refreshToken\":\"" + "a".repeat(129) + "\"}",
                "{");
        // when / then
        for (String body : bodies)
            mvc.perform(post("/api/auth/refresh")
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
        verifyNoInteractions(refresh);
    }

    @Test
    @DisplayName("깨진 로그아웃 본문은 서비스 호출 없이 204이며 쿠키가 없다")
    void malformedLogoutReturnsNoContent() throws Exception {
        // given / when / then
        for (String body : List.of("{", "[]", "{\"refreshToken\":"))
            mvc.perform(post("/api/auth/logout").contentType("application/json").content(body))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        verifyNoInteractions(logout);
    }

    @Test
    @DisplayName("본문 없는 로그아웃은 인증 없이 204를 반환한다")
    void emptyLogoutReturnsNoContent() throws Exception {
        // given / when / then
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(logout).logout(null);
    }
}
