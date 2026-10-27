package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.NonceCodec;
import java.time.Clock;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class NonceKeyConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(NonceKeyConfig.class)
            .withBean("authClock", Clock.class, Clock::systemUTC);

    private String secret(int length) {
        return "mogu.auth.nonce.secret=" + Base64.getEncoder().encodeToString(new byte[length]);
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
        // given / when / then
        runner.withPropertyValues(
                        "spring.profiles.active=local,prod",
                        "mogu.auth.nonce.ephemeral-secret-allowed=true",
                        secret(32))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("31바이트 키와 잘못된 인코딩을 거부한다")
    void invalidSecretFails() {
        // given / when / then
        runner.withPropertyValues(secret(31)).run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("mogu.auth.nonce.secret=invalid!")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("정상 비밀키는 등록되고 문자열 표현에서 숨긴다")
    void validSecretWorks() {
        // given / when / then
        runner.withPropertyValues(secret(32)).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(NonceCodec.class);
            assertThat(context.getBean(NonceProperties.class).toString())
                    .doesNotContain(Base64.getEncoder().encodeToString(new byte[32]));
        });
    }

    @Test
    @DisplayName("로컬에서 임시 키를 허용한다")
    void localAllowsEphemeral() {
        // given / when / then
        runner.withPropertyValues("spring.profiles.active=local", "mogu.auth.nonce.ephemeral-secret-allowed=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("로컬과 테스트 외 프로파일에서 임시 키를 거부한다")
    void otherProfileRejectsEphemeral() {
        // given / when / then
        runner.withPropertyValues("spring.profiles.active=staging", "mogu.auth.nonce.ephemeral-secret-allowed=true")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner socialRunner() {
        return runner.withUserConfiguration(SocialPropertiesConfig.class)
                .withBean(KakaoLoginAdapter.class)
                .withPropertyValues(
                        secret(32),
                        "mogu.auth.social.kakao.issuers=https://kauth.kakao.com",
                        "mogu.auth.social.kakao.jwk-set-uri=https://kauth.kakao.com/.well-known/jwks.json");
    }

    @Test
    @DisplayName("운영에서 카카오 허용 대상이 없으면 기동 실패한다")
    void productionRequiresAudiences() {
        // given / when / then
        socialRunner()
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("쉼표로 구분한 클라이언트 ID는 목록으로 바인딩된다")
    void commaSeparatedAudiencesBind() {
        // given / when / then
        socialRunner()
                .withPropertyValues("mogu.auth.social.kakao.audiences=first,second")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties =
                            context.getBean(OidcProviderProperties.class).kakao();
                    assertThat(properties.audiences()).containsExactly("first", "second");
                    assertThat(properties.connectTimeout()).hasSeconds(2);
                    assertThat(properties.readTimeout()).hasSeconds(3);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OidcProviderProperties.class)
    static class SocialPropertiesConfig {}
}
