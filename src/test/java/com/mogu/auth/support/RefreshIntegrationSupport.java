package com.mogu.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.api.LogoutRequest;
import com.mogu.auth.api.RefreshRequest;
import com.mogu.auth.application.NonceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public abstract class RefreshIntegrationSupport extends LoginIntegrationSupport {
    @TestBean(name = "authClock", methodName = "mutableAuthClock", enforceOverride = true)
    private Clock testClock;

    protected RefreshIntegrationSupport(
            MockMvc mvc, JdbcClient jdbc, NonceService nonces, Clock clock, JsonMapper json) {
        super(mvc, jdbc, nonces, clock, json);
    }

    static Clock mutableAuthClock() {
        return new MutableAuthClock();
    }

    @BeforeEach
    void resetRefreshRecords() {
        assertThat(clock).isSameAs(testClock);
        ((MutableAuthClock) clock).reset();
        jdbc.sql("TRUNCATE auth_refresh_token, auth_social_identity, auth_account, auth_used_nonce, auth_sanctioned_di")
                .update();
    }

    protected ResultActions refresh(String raw) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .contentType("application/json")
                .content(json.writeValueAsString(new RefreshRequest(raw))));
    }

    protected ResultActions logout(String raw) throws Exception {
        return mvc.perform(post("/api/auth/logout")
                .contentType("application/json")
                .content(json.writeValueAsString(new LogoutRequest(raw))));
    }

    protected JsonNode rotate(String raw) throws Exception {
        return json.readTree(refresh(raw)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    protected String raw(JsonNode response) {
        return response.get("refreshToken").asText();
    }

    protected static String hash(String raw) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    protected UUID family(String raw) throws Exception {
        return jdbc.sql("SELECT family_id FROM auth_refresh_token WHERE token_hash = :hash")
                .param("hash", hash(raw))
                .query(UUID.class)
                .single();
    }

    protected void assertRevoked(UUID family, boolean revoked) {
        var values = jdbc.sql("SELECT revoked_at IS NOT NULL FROM auth_refresh_token WHERE family_id = :family")
                .param("family", family)
                .query(Boolean.class)
                .list();
        assertThat(values).isNotEmpty().allMatch(value -> value == revoked);
    }

    protected String invalid(String raw) throws Exception {
        var body = refresh(raw)
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(json.readTree(body))
                .isEqualTo(json.readTree("{\"code\":\"AUTH_INVALID_REFRESH_TOKEN\",\"message\":\"다시 로그인해 주세요.\"}"));
        return body;
    }
}
