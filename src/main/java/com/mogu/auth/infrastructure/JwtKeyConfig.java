package com.mogu.auth.infrastructure;

import com.mogu.common.auth.MemberId;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {
    @Bean("authClock")
    @ConditionalOnMissingBean(name = "authClock")
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtSigningKey signingKey(JwtProperties properties, Environment environment) {
        return JwtKeyLoader.load(
                properties.signingKey(), properties.ephemeralKeyAllowed(), environment, "mogu.auth.jwt");
    }

    @Bean
    JwtEncoder jwtEncoder(@Qualifier("signingKey") JwtSigningKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(signingKey.signingJwkSet()));
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Qualifier("signingKey") JwtSigningKey signingKey,
            JwtProperties properties,
            @Qualifier("authClock") Clock clock) {
        return createDecoder(
                JwtKeyLoader.verificationKeys(signingKey, properties.verificationKeys(), "mogu.auth.jwt"),
                properties,
                clock);
    }

    static NimbusJwtDecoder createDecoder(JWKSet keys, JwtProperties properties, Clock clock) {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt")));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, new ImmutableJWKSet<>(keys)));
        // 시간과 클레임 검증은 주입한 Clock을 사용하는 Spring 검증기에 일임한다.
        processor.setJWTClaimsSetVerifier((claims, context) -> {});
        var decoder = new NimbusJwtDecoder(processor);
        var timestamp = new JwtTimestampValidator(properties.clockSkew());
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamp,
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<List<String>>(
                        "aud", audience -> audience != null && audience.contains(properties.audience())),
                new JwtClaimValidator<Object>("exp", Objects::nonNull),
                new JwtClaimValidator<String>("sub", JwtKeyConfig::validSubject),
                jwt -> "at+jwt".equals(jwt.getHeaders().get("typ"))
                                && present((String) jwt.getHeaders().get("kid"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Invalid token header", null))));
        return decoder;
    }

    private static boolean validSubject(String subject) {
        try {
            MemberId.from(subject);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
