package com.mogu.auth.infrastructure;

import com.mogu.auth.application.DiHasher;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiProperties.class)
public class DiPepperConfig {
    @Bean
    DiHasher diHasher(DiProperties properties, Environment environment) {
        boolean ephemeral = properties.ephemeralPepperAllowed();
        if (ephemeral
                && (environment.acceptsProfiles(Profiles.of("prod"))
                        || !environment.acceptsProfiles(Profiles.of("local", "test")))) throw invalidConfiguration();
        byte[] secret;
        if (properties.pepper() == null || properties.pepper().isBlank()) {
            if (!ephemeral) throw invalidConfiguration();
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            LoggerFactory.getLogger(DiPepperConfig.class).warn("임시 DI pepper를 사용합니다. 재시작하면 기존 DI 해시와 일치하지 않습니다.");
        } else {
            try {
                secret = Base64.getDecoder().decode(properties.pepper());
            } catch (IllegalArgumentException ignored) {
                throw invalidConfiguration();
            }
            if (secret.length < 32) throw invalidConfiguration();
        }
        return new HmacDiHasher(secret);
    }

    private static IllegalStateException invalidConfiguration() {
        return new IllegalStateException("mogu.auth.di.pepper, mogu.auth.di.ephemeral-pepper-allowed");
    }
}
