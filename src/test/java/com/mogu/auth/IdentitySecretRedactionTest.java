package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.support.IdentityIntegrationSupport;
import com.mogu.common.auth.MemberId;
import com.mogu.common.event.MemberSanctioned;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "logging.level.com.mogu=DEBUG",
            "logging.level.org.springframework.security=DEBUG",
            "mogu.auth.di.ephemeral-pepper-allowed=false"
        })
@ExtendWith(OutputCaptureExtension.class)
class IdentitySecretRedactionTest extends IdentityIntegrationSupport {
    private static final Logger log = LoggerFactory.getLogger(IdentitySecretRedactionTest.class);
    private static final String PEPPER = generatePepper();
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactions;
    private final List<String> responses = new ArrayList<>();
    private final List<String> mockDis = new ArrayList<>();

    @Autowired
    IdentitySecretRedactionTest(
            MockMvc mvc,
            JdbcClient jdbc,
            NonceService nonces,
            @Qualifier("authClock") Clock clock,
            JsonMapper json,
            ApplicationEventPublisher publisher,
            PlatformTransactionManager manager) {
        super(mvc, jdbc, nonces, clock, json);
        this.publisher = publisher;
        this.transactions = new TransactionTemplate(manager);
    }

    @DynamicPropertySource
    static void secretProperties(DynamicPropertyRegistry registry) {
        registry.add("mogu.auth.di.pepper", () -> PEPPER);
    }

    @Test
    @DisplayName("가입·연결·제재·입력 오류의 로그와 응답 및 Mock 외 인증 테이블에 DI 원문과 비밀값이 없다")
    void neverExposesIdentitySecrets(CapturedOutput output) throws Exception {
        // given: 출력 캡처가 동작하는지 비밀값 검사보다 먼저 확인한다.
        String marker = "identity-secret-redaction-capture-control-" + UUID.randomUUID();
        log.info("{}", marker);
        assertThat(output.getAll()).contains(marker);
        assertThat(log.isDebugEnabled()).isTrue();
        assertThat(LoggerFactory.getLogger("org.springframework.security").isDebugEnabled())
                .isTrue();

        // when: 신규 가입, 기존 회원 연결, 제재 회원의 재가입 시도
        String first = capturePending("kakao", "redaction-first");
        capture(complete(first, captureVerification(first, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newMember").value(true)));
        String second = capturePending("google", "redaction-second");
        capture(complete(second, captureVerification(second, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newMember").value(false)));
        MemberId member = new MemberId(
                jdbc.sql("SELECT member_id FROM auth_account").query(UUID.class).single());
        transactions.executeWithoutResult(ignored -> publisher.publishEvent(
                new MemberSanctioned(member, clock.instant().plusSeconds(300))));
        String sanctioned = capturePending("kakao", "redaction-sanctioned");
        capture(complete(sanctioned, captureVerification(sanctioned, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_SANCTIONED")));

        // when: 빈 인증 식별자, 잘못된 번호, 실패한 인증 결과
        capture(complete(sanctioned, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT")));
        capture(mock(sanctioned, "invalid-phone", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT")));
        capture(complete(sanctioned, captureVerification(sanctioned, "FAILED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_IDENTITY_VERIFICATION")));

        // then: 모든 HTTP 응답과 모든 Mock 결과를 실제로 수집했음을 확인한다.
        assertThat(responses).hasSize(13).allSatisfy(body -> assertThat(body).isNotBlank());
        assertThat(mockDis).hasSize(4).allSatisfy(di -> assertThat(di).matches("[0-9a-f]{64}"));
        assertThat(count("auth_mock_identity_verification")).isEqualTo(mockDis.size());
        assertNoMockDiInAuthTextColumns();

        byte[] decodedPepper = Base64.getDecoder().decode(PEPPER);
        List<String> secrets = new ArrayList<>(mockDis);
        secrets.add(PEPPER);
        secrets.add(Base64.getEncoder().withoutPadding().encodeToString(decodedPepper));
        secrets.add(HexFormat.of().formatHex(decodedPepper));
        secrets.add(HexFormat.of().withUpperCase().formatHex(decodedPepper));
        secrets.add(Base64.getUrlEncoder().encodeToString(decodedPepper));
        secrets.add(Base64.getUrlEncoder().withoutPadding().encodeToString(decodedPepper));
        for (String secret : secrets) {
            assertThat(output.getAll()).doesNotContain(secret);
            assertThat(responses).allSatisfy(body -> assertThat(body).doesNotContain(secret));
        }
    }

    private JsonNode capture(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        responses.add(body);
        return json.readTree(body);
    }

    private String capturePending(String provider, String subject) throws Exception {
        return capture(login(provider, token(provider, subject))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.registrationRequired").value(true)))
                .get("registrationToken")
                .asText();
    }

    private String captureVerification(String pending, String outcome) throws Exception {
        String id = capture(mock(pending, PHONE_A, outcome).andExpect(status().isOk()))
                .get("verificationId")
                .asText();
        mockDis.add(jdbc.sql("SELECT mock_di FROM auth_mock_identity_verification WHERE verification_id = :id")
                .param("id", id)
                .query(String.class)
                .single());
        return id;
    }

    private void assertNoMockDiInAuthTextColumns() {
        var columns = jdbc.sql("""
            SELECT c.table_schema, c.table_name, c.column_name
            FROM information_schema.columns c
            JOIN information_schema.tables t
              ON t.table_schema = c.table_schema AND t.table_name = c.table_name
            WHERE t.table_type = 'BASE TABLE'
              AND c.table_schema NOT IN ('pg_catalog', 'information_schema')
              AND starts_with(c.table_name, 'auth_')
              AND c.table_name <> 'auth_mock_identity_verification'
              AND c.udt_name IN ('text', 'varchar', 'bpchar', 'citext')
            ORDER BY c.table_schema, c.table_name, c.ordinal_position
            """)
                .query((rs, row) -> new TextColumn(
                        rs.getString("table_schema"), rs.getString("table_name"), rs.getString("column_name")))
                .list();
        assertThat(columns).isNotEmpty();
        assertThat(columns)
                .anyMatch(column ->
                        column.table().equals("auth_account") && column.name().equals("di_hash"));
        for (TextColumn column : columns) {
            String name = quote(column.name());
            var values = jdbc.sql("SELECT " + name + "::text FROM " + quote(column.schema()) + "."
                            + quote(column.table()) + " WHERE " + name + " IS NOT NULL")
                    .query(String.class)
                    .list();
            for (String di : mockDis) {
                assertThat(values)
                        .as("%s.%s.%s", column.schema(), column.table(), column.name())
                        .allSatisfy(value -> assertThat(value).doesNotContain(di));
            }
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String generatePepper() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private record TextColumn(String schema, String table, String name) {}
}
