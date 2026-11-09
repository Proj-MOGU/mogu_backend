package com.mogu.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.api.IdentityVerificationRequest;
import com.mogu.auth.api.MockIdentityVerificationRequest;
import com.mogu.auth.application.NonceService;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public abstract class IdentityIntegrationSupport extends LoginIntegrationSupport {
    protected static final String PHONE_A = "01012345678";
    protected static final String PHONE_B = "01087654321";

    protected IdentityIntegrationSupport(
            MockMvc mvc, JdbcClient jdbc, NonceService nonces, Clock clock, JsonMapper json) {
        super(mvc, jdbc, nonces, clock, json);
    }

    @BeforeEach
    void clearIdentityRecords() {
        jdbc.sql("TRUNCATE auth_refresh_token, auth_social_identity, auth_account, auth_used_nonce, "
                        + "auth_sanctioned_di, auth_used_identity_verification, auth_mock_identity_verification")
                .update();
    }

    protected String pending(String provider, String subject) throws Exception {
        var body = json.readTree(login(provider, token(provider, subject))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(body.get("registrationRequired").asBoolean()).isTrue();
        return body.get("registrationToken").asText();
    }

    protected ResultActions mock(String pending, String phone, String outcome) throws Exception {
        return mvc.perform(post("/api/auth/mock-identity-verifications")
                .header("Authorization", "Bearer " + pending)
                .contentType("application/json")
                .content(json.writeValueAsString(new MockIdentityVerificationRequest(phone, outcome))));
    }

    protected String verification(String pending, String phone) throws Exception {
        return json.readTree(mock(pending, phone, null)
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("verificationId")
                .asText();
    }

    protected ResultActions complete(String pending, String verification) throws Exception {
        return mvc.perform(post("/api/auth/identity-verification")
                .header("Authorization", "Bearer " + pending)
                .contentType("application/json")
                .content(json.writeValueAsString(new IdentityVerificationRequest(verification))));
    }

    protected JsonNode register(String provider, String subject, String phone) throws Exception {
        String pending = pending(provider, subject);
        return json.readTree(complete(pending, verification(pending, phone))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    protected long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
