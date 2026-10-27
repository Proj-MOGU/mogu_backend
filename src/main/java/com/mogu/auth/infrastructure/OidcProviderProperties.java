package com.mogu.auth.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.auth.social")
public record OidcProviderProperties(Provider kakao, Provider google) {
    public record Provider(
            List<String> issuers,
            URI jwkSetUri,
            List<String> audiences,
            Duration connectTimeout,
            Duration readTimeout) {
        public Provider {
            issuers = issuers == null ? List.of() : List.copyOf(issuers);
            audiences = audiences == null
                    ? List.of()
                    : audiences.stream().filter(a -> !a.isBlank()).toList();
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
            if (jwkSetUri == null
                    || issuers.isEmpty()
                    || connectTimeout.isNegative()
                    || connectTimeout.isZero()
                    || readTimeout.isNegative()
                    || readTimeout.isZero()) throw new IllegalArgumentException("mogu.auth.social configuration");
        }
    }
}
