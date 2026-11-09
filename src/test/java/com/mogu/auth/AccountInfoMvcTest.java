package com.mogu.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.api.AccountInfoController;
import com.mogu.auth.application.AccessTokenIssuer;
import com.mogu.auth.application.AccountInfoService;
import com.mogu.auth.application.RegistrationTokenIssuer;
import com.mogu.auth.domain.AuthAccount;
import com.mogu.auth.domain.AuthAccountRepository;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.auth.domain.DiHash;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountInfoController.class)
@ActiveProfiles("test")
@Import({
    AccountInfoService.class,
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
class AccountInfoMvcTest {
    @MockitoBean
    private AuthAccountRepository accounts;

    private final MockMvc mvc;
    private final AccessTokenIssuer tokens;
    private final RegistrationTokenIssuer registrations;

    @Autowired
    AccountInfoMvcTest(MockMvc mvc, AccessTokenIssuer tokens, RegistrationTokenIssuer registrations) {
        this.mvc = mvc;
        this.tokens = tokens;
        this.registrations = registrations;
    }

    @Test
    @DisplayName("인증 회원의 정보만 마스킹하고 캐시 금지 헤더와 함께 반환한다")
    void returnsMaskedInfo() throws Exception {
        // given
        var id = new MemberId(UUID.randomUUID());
        var social = new SocialIdentity(AuthProvider.KAKAO, "subject");
        var account = AuthAccount.register(id, new DiHash("a".repeat(64)), social, Instant.now())
                .updatePersonalInfo("테스트사용자", "01012345678")
                .updateSocialEmail(social, "hong@kakao.com", false);
        when(accounts.findByMemberId(id)).thenReturn(Optional.of(account));
        // when / then
        mvc.perform(get("/api/auth/me/account")
                        .header("Authorization", "Bearer " + tokens.issue(id).value()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.name").value("테스트사용자"))
                .andExpect(jsonPath("$.phoneNumber").value("010-****-5678"))
                .andExpect(jsonPath("$.loginMethods[0].email").value("ho***@kakao.com"));
    }

    @Test
    @DisplayName("정보 없는 기존 계정은 이름·번호·이메일 키를 생략한다")
    void omitsAbsentFields() throws Exception {
        // given
        var id = new MemberId(UUID.randomUUID());
        var account = AuthAccount.register(
                id, new DiHash("a".repeat(64)), new SocialIdentity(AuthProvider.KAKAO, "subject"), Instant.now());
        when(accounts.findByMemberId(id)).thenReturn(Optional.of(account));
        // when / then
        mvc.perform(get("/api/auth/me/account")
                        .header("Authorization", "Bearer " + tokens.issue(id).value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.loginMethods[0].email").doesNotExist())
                .andExpect(jsonPath("$.loginMethods[0].emailVerified").value(false));
    }

    @Test
    @DisplayName("계정 없음·토큰 없음·가입 대기 토큰은 모두 401이다")
    void rejectsMissingAccountAndWrongTokens() throws Exception {
        // given
        var id = new MemberId(UUID.randomUUID());
        when(accounts.findByMemberId(id)).thenReturn(Optional.empty());
        String pending = registrations
                .issue(new SocialIdentity(AuthProvider.KAKAO, "subject"))
                .value();
        // when / then
        mvc.perform(get("/api/auth/me/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        for (String token : new String[] {tokens.issue(id).value(), pending}) {
            mvc.perform(get("/api/auth/me/account").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        }
    }
}
