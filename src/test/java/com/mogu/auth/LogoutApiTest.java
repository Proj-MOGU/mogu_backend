package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.support.RefreshIntegrationSupport;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class LogoutApiTest extends RefreshIntegrationSupport {
    @Autowired
    LogoutApiTest(
            MockMvc mvc, JdbcClient jdbc, NonceService nonces, @Qualifier("authClock") Clock clock, JsonMapper json) {
        super(mvc, jdbc, nonces, clock, json);
    }

    @Test
    @DisplayName("인증 없이 로그아웃하면 해당 패밀리만 폐기하고 쿠키를 발급하지 않는다")
    void revokesOnlyRequestedFamily() throws Exception {
        // given
        account("logout", AccountStatus.ACTIVE, null);
        String first = raw(successfulLogin("logout"));
        String current = raw(rotate(first));
        String other = raw(successfulLogin("logout"));
        // when
        logout(current)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist("Set-Cookie"));
        // then
        assertRevoked(family(first), true);
        assertRevoked(family(other), false);
        invalid(current);
        rotate(other);
    }

    @Test
    @DisplayName("이미 교체한 토큰으로도 패밀리를 폐기하고 반복 로그아웃은 성공한다")
    void usedTokenLogoutIsIdempotent() throws Exception {
        // given
        account("used", AccountStatus.ACTIVE, null);
        String first = raw(successfulLogin("used"));
        String second = raw(rotate(first));
        // when
        logout(first).andExpect(status().isNoContent());
        var before = snapshot();
        logout(first).andExpect(status().isNoContent());
        // then
        assertRevoked(family(first), true);
        assertThat(snapshot()).isEqualTo(before);
        invalid(second);
    }

    @Test
    @DisplayName("없는 토큰과 잘못된 형식과 빈 본문과 깨진 JSON은 변경 없이 204를 반환한다")
    void malformedLogoutIsNoOp() throws Exception {
        // given
        account("unchanged", AccountStatus.ACTIVE, null);
        successfulLogin("unchanged");
        var before = snapshot();
        // when / then
        for (String raw : Arrays.asList(null, "", "invalid", "a".repeat(43), "!".repeat(43))) {
            logout(raw)
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""))
                    .andExpect(header().doesNotExist("Set-Cookie"));
            assertThat(snapshot()).isEqualTo(before);
        }
        for (String body : List.of("", "{", "{\"refreshToken\":", "null", "{}", "[]")) {
            mvc.perform(post("/api/auth/logout").contentType("application/json").content(body))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""))
                    .andExpect(header().doesNotExist("Set-Cookie"));
            assertThat(snapshot()).isEqualTo(before);
        }
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
        assertThat(snapshot()).isEqualTo(before);
    }

    private List<String> snapshot() {
        return jdbc.sql("SELECT row_to_json(t)::text FROM auth_refresh_token t ORDER BY id")
                .query(String.class)
                .list();
    }
}
