package com.mogu.auth.infrastructure;

import com.mogu.auth.application.WithdrawalPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WithdrawalProperties.class)
public class WithdrawalConfig {
    @Bean
    WithdrawalPolicy withdrawalPolicy(WithdrawalProperties properties) {
        return new WithdrawalPolicy(properties.rejoinRestriction());
    }
}
