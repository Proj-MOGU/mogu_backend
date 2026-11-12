package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.application.RateLimitRule;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;

class RateLimitPropertiesTest {
    @Test
    @DisplayName("운영과 테스트 YAML의 모든 제한 설정을 바인딩한다")
    void bindsConfiguredRules() {
        // given / when
        var production = bind("src/main/resources/application.yml");
        var test = bind("src/test/resources/application-test.yml");
        // then
        assertThat(production.queryTimeout()).isEqualTo(Duration.ofMillis(200));
        assertThat(production.rule(RateLimitRule.LOGIN_REQUESTS).limit()).isEqualTo(50);
        assertThat(production.rule(RateLimitRule.LOGIN_FAILURES).refillInterval())
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(production.rule(RateLimitRule.REFRESH_REQUESTS).window()).isEqualTo(Duration.ofSeconds(1));
        assertThat(production.rule(RateLimitRule.IDENTITY_FAILURES).limit()).isEqualTo(10);
        for (var rule : RateLimitRule.values())
            assertThat(test.rule(rule).limit()).isEqualTo(1_000_000);
    }

    private RateLimitProperties bind(String path) {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(path));
        return new Binder(new MapConfigurationPropertySource(yaml.getObject()))
                .bind("mogu.auth.rate-limit", Bindable.of(RateLimitProperties.class))
                .get();
    }
}
