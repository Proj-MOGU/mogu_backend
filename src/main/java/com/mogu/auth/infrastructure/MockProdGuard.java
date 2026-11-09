package com.mogu.auth.infrastructure;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class MockProdGuard {
    @Bean
    SmartInitializingSingleton rejectProductionMock(ApplicationContext context, Environment environment) {
        return () -> {
            if (environment.acceptsProfiles(Profiles.of("prod"))
                    && !context.getBeansOfType(MockIdentityVerificationAdapter.class)
                            .isEmpty())
                throw new IllegalStateException("Mock identity verification is forbidden in prod");
        };
    }
}
