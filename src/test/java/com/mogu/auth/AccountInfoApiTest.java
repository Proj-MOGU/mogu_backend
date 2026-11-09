package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.support.IdentityIntegrationSupport;
import com.mogu.auth.support.MutableAuthClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AccountInfoApiTest extends IdentityIntegrationSupport {
    @TestBean(name = "authClock", methodName = "mutableAuthClock", enforceOverride = true)
    private Clock testClock;

    static Clock mutableAuthClock() {
        return new MutableAuthClock();
    }

    @Autowired
    AccountInfoApiTest(
            MockMvc mvc, JdbcClient jdbc, NonceService nonces, @Qualifier("authClock") Clock clock, JsonMapper json) {
        super(mvc, jdbc, nonces, clock, json);
    }

    private String pendingEmail(String provider, String subject, String email, Object verified) throws Exception {
        return body(login(provider, token(provider, subject, email, verified)).andExpect(status().isOk()))
                .get("registrationToken")
                .asText();
    }

    private String registerEmail(String provider, String subject, String phone, String email, Object verified)
            throws Exception {
        String pending = pendingEmail(provider, subject, email, verified);
        return body(complete(pending, verification(pending, phone)).andExpect(status().isOk()))
                .get("accessToken")
                .asText();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions info(String access) throws Exception {
        return mvc.perform(get("/api/auth/me/account").header("Authorization", "Bearer " + access));
    }

    @Test
    @DisplayName("카카오 가입 후 본명과 가린 번호·이메일을 조회하며 캐시하지 않는다")
    void returnsMaskedAccount() throws Exception {
        // given
        String access = registerEmail("kakao", "first", PHONE_A, "hong@kakao.com", null);
        // when / then
        info(access)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.name").value("테스트사용자"))
                .andExpect(jsonPath("$.phoneNumber").value("010-****-5678"))
                .andExpect(jsonPath("$.loginMethods.length()").value(1))
                .andExpect(jsonPath("$.loginMethods[0].provider").value("KAKAO"))
                .andExpect(jsonPath("$.loginMethods[0].email").value("ho***@kakao.com"))
                .andExpect(jsonPath("$.loginMethods[0].emailVerified").value(false));
    }

    @Test
    @DisplayName("구글 연결 후 각 이메일과 인증 여부를 연결 시각 오름차순으로 조회한다")
    void returnsLinkedMethodsInOrder() throws Exception {
        // given
        String access = registerEmail("kakao", "first", PHONE_A, "hong@kakao.com", null);
        ((MutableAuthClock) clock).advance(Duration.ofSeconds(1));
        registerEmail("google", "second", PHONE_A, "gildong@gmail.com", true);
        // when
        var methods = body(info(access).andExpect(status().isOk())).get("loginMethods");
        // then
        assertThat(methods.size()).isEqualTo(2);
        assertThat(methods.get(0).get("provider").asText()).isEqualTo("KAKAO");
        assertThat(methods.get(0).get("email").asText()).isEqualTo("ho***@kakao.com");
        assertThat(methods.get(1).get("provider").asText()).isEqualTo("GOOGLE");
        assertThat(methods.get(1).get("email").asText()).isEqualTo("gi***@gmail.com");
        assertThat(methods.get(1).get("emailVerified").asBoolean()).isTrue();
        assertThat(Instant.parse(methods.get(0).get("linkedAt").asText()))
                .isBefore(Instant.parse(methods.get(1).get("linkedAt").asText()));
    }

    @Test
    @DisplayName("이메일 없는 계정은 이메일 키를 생략하고 인증 여부는 false다")
    void omitsMissingEmail() throws Exception {
        // given
        String access = registerEmail("kakao", "first", PHONE_A, null, true);
        // when / then
        info(access)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginMethods[0].email").doesNotExist())
                .andExpect(jsonPath("$.loginMethods[0].emailVerified").value(false));
    }

    @Test
    @DisplayName("로그인 이메일을 갱신하고 다음 토큰에 이메일이 없으면 유지한다")
    void updatesEmailAndPreservesWhenAbsent() throws Exception {
        // given
        String access = registerEmail("kakao", "first", PHONE_A, "hong@kakao.com", null);
        // when
        login("kakao", token("kakao", "first", "changed@kakao.com", "true")).andExpect(status().isOk());
        // then
        info(access)
                .andExpect(jsonPath("$.loginMethods[0].email").value("ch***@kakao.com"))
                .andExpect(jsonPath("$.loginMethods[0].emailVerified").value(true));
        // when
        login("kakao", token("kakao", "first")).andExpect(status().isOk());
        // then
        info(access)
                .andExpect(jsonPath("$.loginMethods[0].email").value("ch***@kakao.com"))
                .andExpect(jsonPath("$.loginMethods[0].emailVerified").value(true));
    }

    @Test
    @DisplayName("같은 DI의 연결 인증에서 번호가 바뀌면 새 번호를 저장한다")
    void updatesPhoneForSameDi() throws Exception {
        // given
        String access = registerEmail("kakao", "first", PHONE_A, "hong@kakao.com", null);
        String di = jdbc.sql("SELECT mock_di FROM auth_mock_identity_verification")
                .query(String.class)
                .single();
        String pending = pendingEmail("google", "second", "gildong@gmail.com", true);
        String verification = verification(pending, PHONE_B);
        // 실제 DI는 번호 변경 후에도 동일하다. 번호 기반 Mock DI만 이 조건으로 준비한다.
        jdbc.sql("UPDATE auth_mock_identity_verification SET mock_di=:di WHERE verification_id=:id")
                .param("di", di)
                .param("id", verification)
                .update();
        // when
        complete(pending, verification)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newMember").value(false));
        // then
        info(access).andExpect(jsonPath("$.phoneNumber").value("010-****-4321"));
        assertThat(count("auth_account")).isEqualTo(1);
    }

    @Test
    @DisplayName("토큰 없음과 가입 대기 토큰의 조회를 거부한다")
    void rejectsUnauthorizedTokens() throws Exception {
        // given
        String pending = pending("kakao", "first");
        // when / then
        mvc.perform(get("/api/auth/me/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        info(pending)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("A 토큰으로 조회하면 B의 개인정보와 로그인 방식이 섞이지 않는다")
    void isolatesMembers() throws Exception {
        // given
        String access = registerEmail("kakao", "first", PHONE_A, "hong@kakao.com", null);
        registerEmail("google", "second", PHONE_B, "someone@gmail.com", true);
        // when
        String response = info(access)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // then
        assertThat(response)
                .contains("KAKAO", "ho***@kakao.com")
                .doesNotContain("GOOGLE", "someone@gmail.com", "so***@gmail.com", PHONE_B, "010-****-4321");
    }

    @Test
    @DisplayName("모든 인증 테이블 텍스트에 개인정보 원문이 없고 암호문 컬럼은 버전 접두사를 갖는다")
    void storesOnlyEncryptedPersonalInfo() throws Exception {
        // given
        registerEmail("kakao", "first", PHONE_A, "hong@kakao.com", null);
        registerEmail("google", "second", PHONE_A, "gildong@gmail.com", true);
        // when
        var columns = jdbc.sql(
                        "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='public'"
                                + " AND starts_with(table_name,'auth_') AND udt_name IN"
                                + " ('text','varchar','bpchar','citext')")
                .query((rs, row) -> List.of(rs.getString(1), rs.getString(2)))
                .list();
        // then
        assertThat(columns).isNotEmpty();
        int encryptedFields = 0;
        for (var column : columns) {
            var values = jdbc.sql("SELECT \"" + column.get(1) + "\" FROM \"" + column.get(0) + "\" WHERE \""
                            + column.get(1) + "\" IS NOT NULL")
                    .query(String.class)
                    .list();
            assertThat(values)
                    .allSatisfy(value -> assertThat(value)
                            .doesNotContain("테스트사용자", PHONE_A, "010-1234-5678", "hong@kakao.com", "gildong@gmail.com"));
            if (column.get(1).endsWith("_enc")) {
                encryptedFields++;
                assertThat(values)
                        .isNotEmpty()
                        .allSatisfy(value -> assertThat(value).startsWith("v"));
            }
        }
        assertThat(encryptedFields).isEqualTo(5);
    }

    @Test
    @DisplayName("복호화 실패한 필드만 생략하고 정상 필드와 200 응답을 유지한다")
    void omitsCorruptFields() throws Exception {
        // given
        String access = registerEmail("kakao", "first", PHONE_A, "hong@kakao.com", true);
        jdbc.sql("UPDATE auth_account SET phone_enc='v99.invalid.invalid'").update();
        jdbc.sql("UPDATE auth_social_identity SET email_enc='v99.invalid.invalid'")
                .update();
        // when / then
        info(access)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("테스트사용자"))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.loginMethods[0].email").doesNotExist())
                .andExpect(jsonPath("$.loginMethods[0].emailVerified").value(false));
    }
}
