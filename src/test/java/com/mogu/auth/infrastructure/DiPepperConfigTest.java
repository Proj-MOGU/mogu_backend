package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.DiHasher;
import java.time.Clock;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DiPepperConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DiPepperConfig.class)
            .withBean("authClock", Clock.class, Clock::systemUTC);

    private String secret(int length) {
        return "mogu.auth.di.pepper=" + Base64.getEncoder().encodeToString(new byte[length]);
    }

    @Test
    @DisplayName("비밀키가 없으면 기동 실패한다")
    void missingSecretFails() {
        // given / when / then
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("운영에서 임시 키 허용 설정은 키 유무와 무관하게 거부한다")
    void productionRejectsEphemeral() {
        // given
        var configured = runner.withPropertyValues("mogu.auth.di.ephemeral-pepper-allowed=true", secret(32));
        // when / then
        configured
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local", "prod"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("mogu.auth.di.pepper, mogu.auth.di.ephemeral-pepper-allowed");
                });
        configured
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(DiHasher.class));
    }

    @Test
    @DisplayName("31바이트 키와 잘못된 인코딩을 거부한다")
    void invalidSecretFails() {
        // given / when / then
        runner.withPropertyValues(secret(31)).run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("mogu.auth.di.pepper=invalid!")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("정상 비밀키는 등록되고 문자열 표현에서 숨긴다")
    void validSecretWorks() {
        // given / when / then
        runner.withPropertyValues(secret(32)).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DiHasher.class);
            assertThat(context.getBean(DiProperties.class).toString())
                    .doesNotContain(Base64.getEncoder().encodeToString(new byte[32]));
        });
    }

    @Test
    @DisplayName("로컬에서 임시 키를 허용한다")
    void localAllowsEphemeral() {
        // given / when / then
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues("mogu.auth.di.ephemeral-pepper-allowed=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("로컬과 테스트 외 프로파일에서 임시 키를 거부한다")
    void otherProfileRejectsEphemeral() {
        // given / when / then
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("staging"))
                .withPropertyValues("mogu.auth.di.ephemeral-pepper-allowed=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
