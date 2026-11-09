package com.mogu.member.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.common.auth.MemberId;
import com.mogu.common.auth.WithLoginMember;
import com.mogu.member.application.CreateMemberProfileCommand;
import com.mogu.member.application.MemberProfileService;
import com.mogu.member.application.UpdateMyProfileCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MemberProfileApiTest.FixedClockConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class MemberProfileApiTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    private final MockMvc mvc;
    private final MemberProfileService service;
    private final Clock clock;

    @Autowired
    MemberProfileApiTest(MockMvc mvc, MemberProfileService service, @Qualifier("memberClock") Clock clock) {
        this.mvc = mvc;
        this.service = service;
        this.clock = clock;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        @Qualifier("memberClock")
        Clock fixedMemberClock() {
            return Clock.fixed(Instant.parse("2026-11-09T07:00:00Z").truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
        }
    }

    @Test
    @DisplayName("로그인하지 않으면 세 API 모두 인증 오류를 반환한다")
    void requiresAuthenticationForEveryEndpoint() throws Exception {
        // given / when / then
        mvc.perform(get("/api/members/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"별명12\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/members/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithLoginMember("b74393cb-270c-46de-b148-c0988b238b01")
    @DisplayName("본인의 미완성 프로필은 빈 필드를 생략하고 완성 여부를 반환한다")
    void readsIncompleteProfile() throws Exception {
        // given
        create("b74393cb-270c-46de-b148-c0988b238b01");
        // when / then
        mvc.perform(get("/api/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.nickname").doesNotExist())
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.nicknameChangeableAt").doesNotExist());
    }

    @Test
    @WithLoginMember("b74393cb-270c-46de-b148-c0988b238b02")
    @DisplayName("완성된 본인 프로필은 가입 시각과 다음 변경 가능 시각을 반환한다")
    void readsCompleteProfile() throws Exception {
        // given
        MemberId id = create("b74393cb-270c-46de-b148-c0988b238b02");
        service.updateMyProfile(new UpdateMyProfileCommand(id, nickname(), null));
        String nickname = nickname();
        service.updateMyProfile(new UpdateMyProfileCommand(id, nickname, "안녕하세요"));
        // when / then
        mvc.perform(get("/api/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(id.toString()))
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.bio").value("안녕하세요"))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.registeredAt").value("2026-11-09T06:59:00Z"))
                .andExpect(jsonPath("$.nicknameChangeableAt").value("2026-12-09T07:00:00Z"));
    }

    @Test
    @WithLoginMember("b74393cb-270c-46de-b148-c0988b238b03")
    @DisplayName("프로필 입력은 성공하고 소개를 생략한 재입력은 기존 소개를 지운다")
    void updatesProfileAndClearsOmittedBio() throws Exception {
        // given
        create("b74393cb-270c-46de-b148-c0988b238b03");
        String nickname = nickname();
        // when / then
        update("{\"nickname\":\"  " + nickname + "  \",\"bio\":\" 소개 \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.bio").value("소개"))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.nicknameChangeableAt").doesNotExist());
        update("{\"nickname\":\"" + nickname + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").doesNotExist())
                .andExpect(jsonPath("$.nicknameChangeableAt").doesNotExist());
    }

    @Test
    @WithLoginMember("b74393cb-270c-46de-b148-c0988b238b04")
    @DisplayName("누락과 형식 오류를 구분하고 거절한 입력값은 응답에 넣지 않는다")
    void validatesWithoutEchoingInput() throws Exception {
        // given
        create("b74393cb-270c-46de-b148-c0988b238b04");
        // when / then
        update("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
        update("{\"nickname\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"));
        String rejected = "개인입력!";
        String body = update("{\"nickname\":\"" + rejected + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_NICKNAME"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain(rejected);
        update("{\"nickname\":\"ADMIN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_RESERVED_NICKNAME"));
        update("{\"nickname\":\"" + nickname() + "\",\"bio\":\"안\\n녕\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_INVALID_BIO"));
    }

    @Test
    @WithLoginMember("b74393cb-270c-46de-b148-c0988b238b05")
    @DisplayName("중복 닉네임과 삼십일 이내 재변경은 충돌 오류를 반환한다")
    void rejectsDuplicateAndEarlyChanges() throws Exception {
        // given
        create("b74393cb-270c-46de-b148-c0988b238b05");
        MemberId other = create(UUID.randomUUID().toString());
        String taken = nickname();
        service.updateMyProfile(new UpdateMyProfileCommand(other, taken, null));
        // when / then
        update("{\"nickname\":\"" + taken + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_NICKNAME"));
        update("{\"nickname\":\"" + nickname() + "\"}").andExpect(status().isOk());
        update("{\"nickname\":\"" + nickname() + "\"}").andExpect(status().isOk());
        update("{\"nickname\":\"" + nickname() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NICKNAME_CHANGE_TOO_SOON"));
    }

    @Test
    @WithLoginMember("b74393cb-270c-46de-b148-c0988b238b06")
    @DisplayName("본문에 다른 회원 ID를 넣어도 로그인한 본인만 수정한다")
    void ignoresMemberIdInBody() throws Exception {
        // given
        MemberId self = create("b74393cb-270c-46de-b148-c0988b238b06");
        MemberId other = create(UUID.randomUUID().toString());
        String nickname = nickname();
        // when / then
        update("{\"memberId\":\"" + other + "\",\"nickname\":\"" + nickname + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(self.toString()));
        assertThat(service.getMyProfile(other).completed()).isFalse();
        assertThat(service.getMyProfile(self).nickname()).isEqualTo(nickname);
    }

    @Test
    @WithLoginMember("b74393cb-270c-46de-b148-c0988b238b07")
    @DisplayName("공개 조회는 본인이어도 공개 필드만 반환한다")
    void exposesOnlyPublicFields() throws Exception {
        // given
        MemberId self = create("b74393cb-270c-46de-b148-c0988b238b07");
        MemberId other = create(UUID.randomUUID().toString());
        for (MemberId id : new MemberId[] {self, other}) {
            String nickname = nickname();
            service.updateMyProfile(new UpdateMyProfileCommand(id, nickname, "소개"));
            // when / then
            mvc.perform(get("/api/members/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.memberId").value(id.toString()))
                    .andExpect(jsonPath("$.nickname").value(nickname))
                    .andExpect(jsonPath("$.bio").value("소개"))
                    .andExpect(jsonPath("$.registeredAt").doesNotExist())
                    .andExpect(jsonPath("$.nicknameChangedAt").doesNotExist())
                    .andExpect(jsonPath("$.completed").doesNotExist())
                    .andExpect(jsonPath("$.nicknameChangeableAt").doesNotExist());
        }
    }

    @Test
    @WithLoginMember("b74393cb-270c-46de-b148-c0988b238b08")
    @DisplayName("공개 조회의 미완성 부재 잘못된 ID는 모두 같은 오류 본문을 반환한다")
    void hidesProfileExistence() throws Exception {
        // given
        MemberId incomplete = create(UUID.randomUUID().toString());
        String expected = null;
        // when / then
        for (String id :
                new String[] {incomplete.toString(), UUID.randomUUID().toString(), "not-a-uuid", "1-1-1-1-1"}) {
            String body = mvc.perform(get("/api/members/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEMBER_PROFILE_NOT_FOUND"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (expected == null) expected = body;
            assertThat(body).isEqualTo(expected).doesNotContain(id);
        }
        mvc.perform(get("/api/members/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_PROFILE_NOT_FOUND"));
    }

    private MemberId create(String value) {
        MemberId id = MemberId.from(value);
        service.createIncompleteProfile(
                new CreateMemberProfileCommand(id, clock.instant().minusSeconds(60)));
        return id;
    }

    private String nickname() {
        return "N" + UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }

    private ResultActions update(String body) throws Exception {
        return mvc.perform(
                put("/api/members/me").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
