package com.mogu.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.infrastructure.JsonAuthenticationEntryPoint;
import com.mogu.auth.infrastructure.JwtKeyConfig;
import com.mogu.auth.infrastructure.MemberAuthenticationConverter;
import com.mogu.auth.infrastructure.NonceKeyConfig;
import com.mogu.auth.infrastructure.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NonceController.class)
@ActiveProfiles("test")
@Import({
    NonceController.class,
    NonceService.class,
    NonceKeyConfig.class,
    JwtKeyConfig.class,
    SecurityConfig.class,
    MemberAuthenticationConverter.class,
    JsonAuthenticationEntryPoint.class
})
class NonceApiTest {
    private final MockMvc mvc;

    @Autowired
    NonceApiTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("인증 없이 nonce를 발급하고 캐시 저장을 금지한다")
    void issuesPublicNonce() throws Exception {
        // given / when
        var response = mvc.perform(post("/api/auth/nonce"));
        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.nonce").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("GET 요청은 인증 없이 허용하지 않는다")
    void getIsNotPublic() throws Exception {
        // given / when / then
        mvc.perform(get("/api/auth/nonce")).andExpect(status().isUnauthorized());
    }
}
