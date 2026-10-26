package com.mogu.common.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.infrastructure.AuthWebConfig;
import com.mogu.auth.infrastructure.JsonAuthenticationEntryPoint;
import com.mogu.auth.infrastructure.JwtKeyConfig;
import com.mogu.auth.infrastructure.MemberAuthenticationConverter;
import com.mogu.auth.infrastructure.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(LoginMemberSupportTest.DummyController.class)
@ActiveProfiles("test")
@Import({
    SecurityConfig.class,
    JwtKeyConfig.class,
    MemberAuthenticationConverter.class,
    JsonAuthenticationEntryPoint.class,
    AuthWebConfig.class,
    LoginMemberSupportTest.DummyController.class
})
class LoginMemberSupportTest {
    private final MockMvc mvc;

    @Autowired
    LoginMemberSupportTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("테스트 애노테이션으로 회원 ID를 컨트롤러에 전달한다")
    @WithLoginMember("00000000-0000-7000-8000-000000000002")
    void annotationInjectsMemberId() throws Exception {
        // given
        var request = get("/test/member-support");
        // when
        var result = mvc.perform(request);
        // then
        result.andExpect(status().isOk()).andExpect(content().string("00000000-0000-7000-8000-000000000002"));
    }

    @RestController
    static class DummyController {
        @GetMapping("/test/member-support")
        String me(@LoginMember MemberId memberId) {
            return memberId.toString();
        }
    }
}
