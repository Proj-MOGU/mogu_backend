package com.mogu.auth.infrastructure;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.auth.social")
public record OidcProviderProperties(Provider kakao, Provider google, Duration jwksRefreshMinInterval) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public OidcProviderProperties {
        jwksRefreshMinInterval = jwksRefreshMinInterval == null ? Duration.ofSeconds(30) : jwksRefreshMinInterval;
        if (jwksRefreshMinInterval.toMillis() < 1) throw new IllegalArgumentException("jwks-refresh-min-interval");
    }

    public OidcProviderProperties(Provider kakao, Provider google) {
        this(kakao, google, Duration.ofSeconds(30));
    }

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
