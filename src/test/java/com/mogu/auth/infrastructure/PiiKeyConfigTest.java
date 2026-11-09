package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.PiiCipher;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PiiKeyConfigTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PiiKeyConfig.class);

    private String key(int size) {
        return Base64.getEncoder().encodeToString(new byte[size]);
    }

    @Test
    @DisplayName("키가 없고 임시 키가 금지되면 기동을 거부한다")
    void rejectsMissingKeys() {
        // given / when / then
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("같은 임시 키 설정은 운영에서 실패하고 테스트에서는 성공한다")
    void rejectsProductionEphemeral() {
        // given
        var configured = runner.withPropertyValues("mogu.auth.pii.ephemeral-key-allowed=true");
        // when / then
        configured
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("test", "prod"))
                .run(ctx -> assertThat(ctx).hasFailed());
        configured
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("test"))
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(PiiCipher.class));
    }

    @Test
    @DisplayName("31바이트 키와 누락된 활성 버전을 거부한다")
    void rejectsInvalidKeys() {
        // given / when / then
        runner.withPropertyValues("mogu.auth.pii.keys.1=" + key(31), "mogu.auth.pii.active-key-version=1")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("mogu.auth.pii.keys.1=" + key(32), "mogu.auth.pii.active-key-version=2")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1", "-1", "a", "1x"})
    @DisplayName("숫자 외 버전 이름을 거부한다")
    void rejectsInvalidVersion(String version) {
        // given / when / then
        runner.withPropertyValues(
                        "mogu.auth.pii.encryption-keys=" + version + "=" + key(32),
                        "mogu.auth.pii.active-key-version=" + version)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("맵과 환경변수용 문자열 키 설정이 동작하고 키를 출력하지 않는다")
    void acceptsKeysAndRedacts() {
        // given / when / then
        for (String property : new String[] {"keys.1=" + key(32), "encryption-keys=1=" + key(32) + ",2=" + key(32)}) {
            runner.withPropertyValues("mogu.auth.pii." + property, "mogu.auth.pii.active-key-version=1")
                    .run(ctx -> {
                        assertThat(ctx).hasNotFailed().hasSingleBean(PiiCipher.class);
                        assertThat(ctx.getBean(PiiProperties.class).toString()).doesNotContain(key(32));
                    });
        }
    }
}
