package com.mogu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.NonceService;
import com.mogu.auth.domain.AccountStatus;
import com.mogu.auth.support.MutableAuthClock;
import com.mogu.auth.support.RefreshIntegrationSupport;
import com.mogu.common.event.MemberSanctioned;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class RefreshApiTest extends RefreshIntegrationSupport {
    private final ApplicationEventPublisher publisher;

    @Autowired
    RefreshApiTest(
            MockMvc mvc,
            JdbcClient jdbc,
            NonceService nonces,
            @Qualifier("authClock") Clock clock,
            JsonMapper json,
            ApplicationEventPublisher publisher) {
        super(mvc, jdbc, nonces, clock, json);
        this.publisher = publisher;
    }

    @Test
    @DisplayName("인증 없이 교체하면 새 토큰으로 같은 회원을 인증하고 해시만 저장한다")
    void rotatesAndAuthenticatesSameMember() throws Exception {
        // given
        var member = account("active", AccountStatus.ACTIVE, null);
        var login = successfulLogin("active");
        var first = raw(login);
        ((MutableAuthClock) clock).advance(Duration.ofSeconds(1));
        // when
        var response = refresh(first)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .getResponse();
        var body = json.readTree(response.getContentAsString());
        // then
        assertThat(body.get("accessToken").asText())
                .isNotEqualTo(login.get("accessToken").asText());
        assertThat(raw(body)).isNotEqualTo(first);
        assertThat(family(raw(body))).isEqualTo(family(first));
        assertThat(Instant.parse(body.get("refreshTokenExpiresAt").asText()))
                .isEqualTo(clock.instant().plus(Duration.ofDays(30)));
        assertThat(Instant.parse(body.get("accessTokenExpiresAt").asText()))
                .isEqualTo(clock.instant()
                        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                        .plus(Duration.ofMinutes(15)));
        mvc.perform(get("/test/login-member")
                        .header(
                                "Authorization",
                                "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(content().string(member.toString()));
        assertThat(jdbc.sql("SELECT token_hash FROM auth_refresh_token")
                        .query(String.class)
                        .list())
                .containsExactlyInAnyOrder(hash(first), hash(raw(body)));
        assertThat(jdbc.sql("SELECT row_to_json(t)::text FROM auth_refresh_token t")
                        .query(String.class)
                        .list())
                .allSatisfy(row -> assertThat(row).doesNotContain(first, raw(body)));
    }

    @Test
    @DisplayName("세 번 연속 교체하면 같은 패밀리 네 행 중 앞의 세 행이 사용 처리된다")
    void rotatesThreeTimes() throws Exception {
        // given
        account("chain", AccountStatus.ACTIVE, null);
        String current = raw(successfulLogin("chain"));
        var family = family(current);
        // when
        for (int i = 0; i < 3; i++) current = raw(rotate(current));
        // then
        assertThat(family(current)).isEqualTo(family);
        assertThat(jdbc.sql("SELECT family_id FROM auth_refresh_token")
                        .query(java.util.UUID.class)
                        .list())
                .hasSize(4)
                .containsOnly(family);
        assertThat(jdbc.sql("SELECT count(*) FROM auth_refresh_token WHERE used_at IS NOT NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(3);
        assertRevoked(family, false);
    }

    @Test
    @DisplayName("재사용 시 패밀리 전체 폐기를 커밋하고 경고에 원문과 해시를 남기지 않는다")
    void reuseRevokesFamilyAndRedactsLogs(CapturedOutput output) throws Exception {
        // given
        account("reuse", AccountStatus.ACTIVE, null);
        String first = raw(successfulLogin("reuse"));
        String second = raw(rotate(first));
        var family = family(first);
        // when
        String reusedBody = invalid(first);
        // then
        assertThat(invalid(second)).isEqualTo(reusedBody);
        assertRevoked(family, true);
        assertThat(output.getAll())
                .contains("WARN", "Refresh Token 재사용 탐지", "familyId=" + family)
                .doesNotContain(first, second, hash(first), hash(second));
    }

    @Test
    @DisplayName("재사용 탐지는 같은 회원의 다른 기기 패밀리에 영향을 주지 않는다")
    void reuseLeavesOtherFamilyActive() throws Exception {
        // given
        account("devices", AccountStatus.ACTIVE, null);
        String first = raw(successfulLogin("devices"));
        String other = raw(successfulLogin("devices"));
        rotate(first);
        // when
        invalid(first);
        // then
        rotate(other);
        assertRevoked(family(first), true);
        assertRevoked(family(other), false);
    }

    @Test
    @DisplayName("만료와 폐기와 재사용과 잘못된 토큰의 401 본문이 동일하다")
    void invalidReasonsHaveIdenticalResponses() throws Exception {
        // given
        account("errors", AccountStatus.ACTIVE, null);
        String expired = raw(successfulLogin("errors"));
        String revoked = raw(successfulLogin("errors"));
        String reused = raw(successfulLogin("errors"));
        rotate(reused);
        logout(revoked).andExpect(status().isNoContent());
        // when
        String expected = invalid(reused);
        assertThat(invalid(revoked)).isEqualTo(expected);
        ((MutableAuthClock) clock).advance(Duration.ofDays(30).plusSeconds(1));
        // then
        assertThat(invalid(expired)).isEqualTo(expected);
        assertRevoked(family(expired), false);
        for (String value : List.of("a".repeat(43), "a".repeat(42), "!".repeat(43)))
            assertThat(invalid(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("비어 있는 토큰은 공통 입력 오류로 거부한다")
    void rejectsBlankInput(String value) throws Exception {
        // given / when / then
        refresh(value)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    @DisplayName("129자 토큰은 공통 입력 오류로 거부한다")
    void rejectsOversizedInput() throws Exception {
        // given / when / then
        refresh("a".repeat(129))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    @DisplayName("제재 이벤트 이전에 발급한 토큰을 거부한다")
    void rejectsSanctionedMember() throws Exception {
        // given
        var member = account("sanction", AccountStatus.ACTIVE, null);
        String raw = raw(successfulLogin("sanction"));
        // when
        publisher.publishEvent(new MemberSanctioned(member, null));
        // then
        invalid(raw);
        assertRevoked(family(raw), true);
    }

    @Test
    @DisplayName("이벤트 없이 계정만 제재되어도 교체 시 패밀리 전체를 폐기한다")
    void revokesFamilyWhenSanctionHandlerWasBypassed() throws Exception {
        // given
        var member = account("bypass", AccountStatus.ACTIVE, null);
        String first = raw(successfulLogin("bypass"));
        String second = raw(rotate(first));
        jdbc.sql("UPDATE auth_account SET status = 'SANCTIONED' WHERE member_id = :member")
                .param("member", member.value())
                .update();
        // when
        invalid(second);
        // then
        assertRevoked(family(first), true);
    }
}
