package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mogu.auth.application.RateLimiter;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

class SecurityConfigContextTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(WebSecurityConfiguration.class, SecurityConfig.class)
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(JsonAuthenticationEntryPoint.class)
            .withBean(MemberAuthenticationConverter.class, () -> mock(MemberAuthenticationConverter.class))
            .withBean(
                    PendingRegistrationAuthenticationConverter.class,
                    () -> mock(PendingRegistrationAuthenticationConverter.class))
            .withBean("jwtDecoder", JwtDecoder.class, () -> mock(JwtDecoder.class))
            .withBean("registrationJwtDecoder", JwtDecoder.class, () -> mock(JwtDecoder.class));

    @Test
    @DisplayName("DataSource가 있는데 RateLimiter가 없으면 이유를 알리고 기동에 실패한다")
    void failsWhenDataSourceExistsWithoutRateLimiter() {
        // given
        var contextRunner = runner.withBean(DataSource.class, () -> mock(DataSource.class));
        // when / then
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DataSource bean exists but RateLimiter bean is missing");
        });
    }

    @Test
    @DisplayName("DataSource와 RateLimiter가 없는 슬라이스는 필터 없이 기동한다")
    void startsWithoutDataSourceAndRateLimiter() {
        // given / when / then
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DataSource.class).doesNotHaveBean(RateLimiter.class);
            assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(2);
            for (var chain : context.getBeansOfType(SecurityFilterChain.class).values()) {
                assertThat(chain.getFilters()).noneMatch(AuthRateLimitFilter.class::isInstance);
            }
        });
    }

    @Test
    @DisplayName("DataSource와 RateLimiter가 모두 있으면 두 체인에 필터를 등록하고 기동한다")
    void startsWithDataSourceAndRateLimiter() {
        // given
        var contextRunner = runner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(RateLimiter.class, () -> mock(RateLimiter.class));
        // when / then
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(2);
            for (var chain : context.getBeansOfType(SecurityFilterChain.class).values()) {
                assertThat(chain.getFilters())
                        .filteredOn(AuthRateLimitFilter.class::isInstance)
                        .hasSize(1);
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityConfiguration {}
}
