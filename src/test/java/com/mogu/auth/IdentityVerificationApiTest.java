package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.support.IdentityIntegrationSupport;
import com.mogu.auth.support.MutableAuthClock;
import com.mogu.common.auth.MemberId;
import com.mogu.common.event.MemberRegistered;
import com.mogu.common.event.MemberSanctionLifted;
import com.mogu.common.event.MemberSanctioned;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
class IdentityVerificationApiTest extends IdentityIntegrationSupport {
    @TestBean(name = "authClock", methodName = "mutableAuthClock", enforceOverride = true)
    private Clock testClock;

    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactions;

    @Autowired
    IdentityVerificationApiTest(
            MockMvc mvc,
            JdbcClient jdbc,
            NonceService nonces,
            @Qualifier("authClock") Clock clock,
            JsonMapper json,
            ApplicationEventPublisher publisher,
            org.springframework.transaction.PlatformTransactionManager manager) {
        super(mvc, jdbc, nonces, clock, json);
        this.publisher = publisher;
        this.transactions = new TransactionTemplate(manager);
    }

    private MemberId member() {
        return new MemberId(
                jdbc.sql("SELECT member_id FROM auth_account").query(UUID.class).single());
    }

    private void publish(Object event) {
        transactions.executeWithoutResult(ignored -> publisher.publishEvent(event));
    }

    @Test
    @DisplayName("신규 가입은 UUIDv7 회원과 토큰 두 개를 만들고 가입 이벤트를 한 번 발행한다")
    void registersAndPublishesOnce(ApplicationEvents events) throws Exception {
        // given / when
        var response = register("kakao", "first", PHONE_A);
        // then
        assertThat(response.get("newMember").asBoolean()).isTrue();
        assertThat(response.get("accessToken").asText()).isNotBlank();
        assertThat(response.get("refreshToken").asText()).isNotBlank();
        assertThat(Instant.parse(response.get("accessTokenExpiresAt").asText())).isAfter(clock.instant());
        assertThat(Instant.parse(response.get("refreshTokenExpiresAt").asText()))
                .isAfter(clock.instant());
        assertThat(count("auth_account")).isEqualTo(1);
        assertThat(count("auth_social_identity")).isEqualTo(1);
        assertThat(member().value().version()).isEqualTo(7);
        assertThat(jdbc.sql("SELECT di_hash FROM auth_account")
                        .query(String.class)
                        .single())
                .matches("[0-9a-f]{64}");
        assertThat(jdbc.sql("SELECT status FROM auth_account")
                        .query(String.class)
                        .single())
                .isEqualTo("ACTIVE");
        mvc.perform(get("/test/login-member")
                        .header(
                                "Authorization",
                                "Bearer " + response.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(content().string(member().toString()));
        var registered = events.stream(MemberRegistered.class).toList();
        assertThat(registered).hasSize(1);
        assertThat(registered.getFirst().memberId()).isEqualTo(member());
        assertThat(registered.getFirst().registeredAt())
                .isEqualTo(jdbc.sql("SELECT created_at FROM auth_account")
                        .query((rs, n) -> rs.getTimestamp(1).toInstant())
                        .single());
        assertThat(successfulLogin("first").get("registrationRequired").asBoolean())
                .isFalse();
    }

    @Test
    @DisplayName("같은 번호의 구글 계정을 기존 카카오 회원에게 연결하고 이후 바로 로그인한다")
    void linksGoogleWithoutRegistrationEvent(ApplicationEvents events) throws Exception {
        // given
        register("kakao", "first", PHONE_A);
        var id = member();
        events.clear();
        // when
        var response = register("google", "second", PHONE_A);
        // then
        assertThat(response.get("newMember").asBoolean()).isFalse();
        assertThat(member()).isEqualTo(id);
        assertThat(count("auth_social_identity")).isEqualTo(2);
        assertThat(events.stream(MemberRegistered.class)).isEmpty();
        login("google", token("google", "second"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationRequired").value(false));
        mvc.perform(get("/test/login-member")
                        .header(
                                "Authorization",
                                "Bearer " + response.get("accessToken").asText()))
                .andExpect(content().string(id.toString()));
    }

    @Test
    @DisplayName("제재는 모든 활성 리프레시 토큰을 폐기하고 재가입 인증 사용 기록을 커밋한다")
    void sanctionBlocksAndConsumesVerification(ApplicationEvents events) throws Exception {
        // given
        register("kakao", "first", PHONE_A);
        successfulLogin("first");
        publish(new MemberSanctioned(member(), clock.instant().plusSeconds(300)));
        String pending = pending("google", "second");
        String verification = verification(pending, PHONE_A);
        events.clear();
        // when / then
        complete(pending, verification)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_SANCTIONED"));
        complete(pending, verification)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_IDENTITY_VERIFICATION"));
        assertThat(jdbc.sql("SELECT count(*) FROM auth_refresh_token WHERE revoked_at IS NULL")
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(count("auth_refresh_token")).isEqualTo(2);
        assertThat(count("auth_used_identity_verification")).isEqualTo(2);
        login("kakao", token("first")).andExpect(status().isForbidden());
        assertThat(events.stream(MemberRegistered.class)).isEmpty();
    }

    @Test
    @DisplayName("무기한 제재도 재가입을 막고 해제하면 연결과 로그인이 가능하다")
    void liftsPermanentSanction() throws Exception {
        // given
        register("kakao", "first", PHONE_A);
        publish(new MemberSanctioned(member(), null));
        String pending = pending("google", "second");
        // when / then
        complete(pending, verification(pending, PHONE_A)).andExpect(status().isForbidden());
        publish(new MemberSanctionLifted(member()));
        complete(pending, verification(pending, PHONE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newMember").value(false));
        assertThat(count("auth_sanctioned_di")).isZero();
        assertThat(successfulLogin("first").get("registrationRequired").asBoolean())
                .isFalse();
    }

    @Test
    @DisplayName("다른 번호는 별개 회원으로 신규 가입한다")
    void differentPhoneCreatesAnotherMember() throws Exception {
        // given
        register("kakao", "first", PHONE_A);
        // when
        var response = register("google", "second", PHONE_B);
        // then
        assertThat(response.get("newMember").asBoolean()).isTrue();
        assertThat(count("auth_account")).isEqualTo(2);
    }

    @Test
    @DisplayName("인증 결과 재사용과 가입 완료 토큰의 새 인증 제출을 각각 거부한다")
    void rejectsReplays() throws Exception {
        // given
        String pending = pending("kakao", "first");
        String verification = verification(pending, PHONE_A);
        complete(pending, verification).andExpect(status().isOk());
        // when / then
        complete(pending, verification)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_IDENTITY_VERIFICATION"));
        complete(pending, verification(pending, PHONE_B)).andExpect(status().isUnauthorized());
        assertThat(count("auth_account")).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 가입 시도의 인증 결과를 거부하며 사용 기록과 이벤트를 남기지 않는다")
    void rejectsAnotherRegistration(ApplicationEvents events) throws Exception {
        // given
        String first = pending("kakao", "first");
        String second = pending("google", "second");
        String verification = verification(first, PHONE_A);
        // when / then
        complete(second, verification)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_IDENTITY_VERIFICATION"));
        assertThat(count("auth_used_identity_verification")).isZero();
        assertThat(events.stream(MemberRegistered.class)).isEmpty();
        complete(first, verification).andExpect(status().isOk());
    }

    @Test
    @DisplayName("실패한 인증과 없는 식별자 및 형식 오류를 거부한다")
    void rejectsFailedAndMissingVerification(ApplicationEvents events) throws Exception {
        // given
        String pending = pending("kakao", "first");
        String failed = json.readTree(mock(pending, PHONE_A, "FAILED")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("verificationId")
                .asText();
        // when / then
        for (String id : new String[] {failed, "a".repeat(32), "invalid"}) {
            complete(pending, id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_IDENTITY_VERIFICATION"));
        }
        assertThat(count("auth_used_identity_verification")).isZero();
        assertThat(events.stream(MemberRegistered.class)).isEmpty();
    }

    @Test
    @DisplayName("같은 공급자의 다른 계정 연결을 거부하고 인증 사용 기록은 롤백한다")
    void rejectsDuplicateProvider(ApplicationEvents events) throws Exception {
        // given
        register("kakao", "first", PHONE_A);
        String pending = pending("kakao", "second");
        String verification = verification(pending, PHONE_A);
        events.clear();
        // when / then
        for (int i = 0; i < 2; i++)
            complete(pending, verification)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUTH_PROVIDER_ALREADY_LINKED"));
        assertThat(count("auth_social_identity")).isEqualTo(1);
        assertThat(count("auth_used_identity_verification")).isEqualTo(1);
        assertThat(events.stream(MemberRegistered.class)).isEmpty();
    }

    @Test
    @DisplayName("Access Token이나 토큰 없는 본인인증 요청은 거부한다")
    void rejectsWrongAuthentication() throws Exception {
        // given
        var response = register("kakao", "first", PHONE_A);
        // when / then
        complete(response.get("accessToken").asText(), "a".repeat(32)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/identity-verification")
                        .contentType("application/json")
                        .content("{\"verificationId\":\"missing\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("잘못된 번호와 결과는 입력 오류로 반환하고 번호 원문을 응답하지 않는다")
    void rejectsMockInput() throws Exception {
        // given
        String pending = pending("kakao", "first");
        // when / then
        var response = mock(pending, "0212345678", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(response).doesNotContain("0212345678");
        mock(pending, PHONE_A, "OTHER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
        assertThat(count("auth_mock_identity_verification")).isZero();
    }

    @Test
    @DisplayName("모든 인증 테이블과 출력에 전화번호가 없고 DI는 다시 해시해 저장한다")
    void neverStoresOrLogsPhone(CapturedOutput output) throws Exception {
        // given / when
        register("kakao", "first", PHONE_A);
        register("google", "second", PHONE_B);
        // then
        var tables = jdbc.sql(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE" + " 'auth_%'")
                .query(String.class)
                .list();
        assertThat(tables).hasSizeGreaterThanOrEqualTo(7);
        for (String table : tables) {
            assertThat(jdbc.sql("SELECT row_to_json(t)::text FROM " + table + " t")
                            .query(String.class)
                            .list())
                    .allSatisfy(row -> assertThat(row).doesNotContain(PHONE_A, PHONE_B));
        }
        var mockDis = jdbc.sql("SELECT mock_di FROM auth_mock_identity_verification")
                .query(String.class)
                .list();
        assertThat(jdbc.sql("SELECT di_hash FROM auth_account")
                        .query(String.class)
                        .list())
                .doesNotContainAnyElementsOf(mockDis);
        assertThat(output.getAll()).doesNotContain(PHONE_A, PHONE_B);
    }

    @Test
    @DisplayName("발행 트랜잭션 롤백 시 제재 상태와 DI 및 토큰 폐기를 함께 롤백한다")
    void sanctionRollsBackWithPublisher() throws Exception {
        // given
        register("kakao", "first", PHONE_A);
        // when
        transactions.executeWithoutResult(tx -> {
            publisher.publishEvent(new MemberSanctioned(member(), null));
            tx.setRollbackOnly();
        });
        // then
        assertThat(jdbc.sql("SELECT status FROM auth_account")
                        .query(String.class)
                        .single())
                .isEqualTo("ACTIVE");
        assertThat(count("auth_sanctioned_di")).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM auth_refresh_token WHERE revoked_at IS NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @org.junit.jupiter.api.BeforeEach
    void resetClock() {
        assertThat(clock).isSameAs(testClock);
        ((MutableAuthClock) clock).reset();
    }

    @Test
    @DisplayName("인증 생성 후 정확히 10분에는 허용하고 10분을 넘으면 거부한다")
    void rejectsExpiredVerification(ApplicationEvents events) throws Exception {
        // given
        String pending = pending("kakao", "first");
        String id = verification(pending, PHONE_A);
        var mutable = (MutableAuthClock) clock;
        // when
        mutable.advance(java.time.Duration.ofSeconds(601));
        // then
        complete(pending, id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_IDENTITY_VERIFICATION"));
        assertThat(count("auth_used_identity_verification")).isZero();
        assertThat(events.stream(MemberRegistered.class)).isEmpty();
        mutable.advance(java.time.Duration.ofSeconds(-1));
        complete(pending, id).andExpect(status().isOk());
    }

    static Clock mutableAuthClock() {
        return new MutableAuthClock();
    }
}
