package com.mogu.common.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.infrastructure.JsonAuthenticationEntryPoint;
import com.mogu.auth.infrastructure.JwtKeyConfig;
import com.mogu.auth.infrastructure.MemberAuthenticationConverter;
import com.mogu.auth.infrastructure.PendingRegistrationAuthenticationConverter;
import com.mogu.auth.infrastructure.PiiKeyConfig;
import com.mogu.auth.infrastructure.RegistrationKeyConfig;
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

@WebMvcTest(CurrentMemberSliceTest.DummyController.class)
@ActiveProfiles("test")
@Import({
    PiiKeyConfig.class,
    SecurityConfig.class,
    RegistrationKeyConfig.class,
    PendingRegistrationAuthenticationConverter.class,
    JwtKeyConfig.class,
    MemberAuthenticationConverter.class,
    JsonAuthenticationEntryPoint.class,
    CurrentMemberSliceTest.DummyController.class
})
class CurrentMemberSliceTest {
    private final MockMvc mvc;

    @Autowired
    CurrentMemberSliceTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("다른 도메인의 MVC 슬라이스 테스트에서도 CurrentMember와 @LoginMember를 별도 등록 없이 쓴다")
    @WithLoginMember("00000000-0000-7000-8000-000000000002")
    void exposesCurrentMemberAndLoginMemberWithoutExplicitRegistration() throws Exception {
        // given
        var memberId = "00000000-0000-7000-8000-000000000002";
        var request = get("/test/current-member-slice");
        // when
        var result = mvc.perform(request);
        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMember").value(memberId))
                .andExpect(jsonPath("$.loginMember").value(memberId));
    }

    @RestController
    static class DummyController {
        private final CurrentMember currentMember;

        DummyController(CurrentMember currentMember) {
            this.currentMember = currentMember;
        }

        @GetMapping("/test/current-member-slice")
        CurrentMemberResponse current(@LoginMember MemberId memberId) {
            return new CurrentMemberResponse(currentMember.find().orElseThrow().toString(), memberId.toString());
        }
    }

    record CurrentMemberResponse(String currentMember, String loginMember) {}
}
