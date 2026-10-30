package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mogu.auth.api.MockIdentityVerificationController;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

class MockProdGuardTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(MockProdGuard.class);

    @Test
    @DisplayName("운영과 로컬 혼합 프로파일에서 Mock 빈이 있으면 기동을 거부한다")
    void rejectsMockInProduction() {
        // given / when / then
        for (String profiles : new String[] {"prod", "local,prod", "test,prod"}) {
            runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles.split(",")))
                    .withUserConfiguration(ForcedMockConfig.class)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasMessageContaining("Mock identity verification is forbidden in prod");
                    });
        }
    }

    @Test
    @DisplayName("운영에서는 Mock 컨트롤러와 어댑터가 등록되지 않는다")
    void productionHasNoMockBeans() {
        // given / when / then
        mockRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(MockIdentityVerificationAdapter.class)
                        .doesNotHaveBean(MockIdentityVerificationController.class));
    }

    @Test
    @DisplayName("테스트에서는 같은 설정으로 Mock 컨트롤러와 어댑터가 등록된다")
    void testHasMockBeans() {
        // given / when / then
        mockRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(MockIdentityVerificationAdapter.class)
                        .hasSingleBean(MockIdentityVerificationController.class));
    }

    private ApplicationContextRunner mockRunner() {
        return runner.withUserConfiguration(
                        MockIdentityVerificationAdapter.class, MockIdentityVerificationController.class)
                .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
                .withBean("authClock", Clock.class, Clock::systemUTC);
    }

    @Configuration(proxyBeanMethods = false)
    static class ForcedMockConfig {
        @Bean
        MockIdentityVerificationAdapter forcedMockAdapter() {
            return mock(MockIdentityVerificationAdapter.class);
        }
    }
}
