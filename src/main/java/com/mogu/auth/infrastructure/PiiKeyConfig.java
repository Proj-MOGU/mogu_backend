package com.mogu.auth.infrastructure;

import com.mogu.auth.application.PiiCipher;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PiiProperties.class)
public class PiiKeyConfig {
    @Bean
    PiiCipher piiCipher(PiiProperties properties, Environment environment) {
        try {
            if (properties.ephemeralKeyAllowed()
                    && (environment.acceptsProfiles(Profiles.of("prod"))
                            || !environment.acceptsProfiles(Profiles.of("local", "test")))) throw invalid();
            var configured = new HashMap<>(properties.keys());
            if (properties.encryptionKeys() != null
                    && !properties.encryptionKeys().isBlank()) {
                for (String entry : properties.encryptionKeys().split(",", -1)) {
                    String[] pair = entry.split("=", 2);
                    if (pair.length != 2 || configured.putIfAbsent(pair[0], pair[1]) != null) throw invalid();
                }
            }
            var keys = new HashMap<String, byte[]>();
            configured.forEach(
                    (version, value) -> keys.put(version, Base64.getDecoder().decode(value)));
            String active = properties.activeKeyVersion();
            if (keys.isEmpty()) {
                if (!properties.ephemeralKeyAllowed()) throw invalid();
                byte[] key = new byte[32];
                new SecureRandom().nextBytes(key);
                if (active == null || active.isBlank()) active = "1";
                keys.put(active, key);
                LoggerFactory.getLogger(PiiKeyConfig.class).warn("임시 개인정보 암호화 키를 사용합니다. 재시작하면 기존 값을 복호화할 수 없습니다.");
            }
            return new AesGcmPiiCipher(keys, active);
        } catch (RuntimeException ignored) {
            throw invalid();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Invalid mogu.auth.pii configuration");
    }
}
