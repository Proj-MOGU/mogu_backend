package com.mogu.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.DummyController.class)
@Import({SecurityConfig.class, SecurityConfigTest.DummyController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("미인증 요청은 세션을 생성하지 않고 401을 반환한다")
    void unauthenticatedRequestIsRejectedWithoutCreatingSession() throws Exception {
        // when
        var response = mockMvc.perform(get("/test/private"));

        // then
        response.andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(result ->
                        assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    @DisplayName("CSRF 토큰 없는 미인증 POST 요청은 401을 반환한다")
    void unauthenticatedPostWithoutCsrfTokenIsUnauthorized() throws Exception {
        // when
        var result = mockMvc.perform(post("/test/private"));

        // then
        result.andExpect(status().isUnauthorized());
    }

    @RestController
    static class DummyController {

        @GetMapping("/test/private")
        String privateEndpoint() {
            return "private";
        }
    }
}
