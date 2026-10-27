package com.mogu.auth.infrastructure;

import com.mogu.auth.application.NonceCodec;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NonceProperties.class)
public class NonceKeyConfig {
    @Bean
    NonceCodec nonceCodec(NonceProperties properties, Environment environment, @Qualifier("authClock") Clock clock) {
        boolean ephemeral = properties.ephemeralSecretAllowed();
        if (ephemeral
                && (environment.acceptsProfiles(Profiles.of("prod"))
                        || !environment.acceptsProfiles(Profiles.of("local", "test")))) throw invalidConfiguration();
        byte[] secret;
        if (properties.secret() == null || properties.secret().isBlank()) {
            if (!ephemeral) throw invalidConfiguration();
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            LoggerFactory.getLogger(NonceKeyConfig.class).warn("임시 nonce 서명 키를 사용합니다. 재시작하면 기존 nonce는 무효가 됩니다.");
        } else {
            try {
                secret = Base64.getDecoder().decode(properties.secret());
            } catch (IllegalArgumentException ignored) {
                throw invalidConfiguration();
            }
            if (secret.length < 32) throw invalidConfiguration();
        }
        return new HmacNonceCodec(secret, properties.ttl(), clock);
    }

    private static IllegalStateException invalidConfiguration() {
        return new IllegalStateException("mogu.auth.nonce.secret, mogu.auth.nonce.ephemeral-secret-allowed");
    }
}
