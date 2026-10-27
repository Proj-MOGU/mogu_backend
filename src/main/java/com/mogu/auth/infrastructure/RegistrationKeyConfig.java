package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AuthProvider;
import com.mogu.common.auth.MemberId;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RegistrationJwtProperties.class)
public class RegistrationKeyConfig {
    @Bean
    JwtSigningKey registrationSigningKey(
            RegistrationJwtProperties properties,
            Environment environment,
            @Qualifier("signingKey") JwtSigningKey accessKey) {
        var key = JwtKeyLoader.load(
                properties.signingKey(), properties.ephemeralKeyAllowed(), environment, "mogu.auth.registration-jwt");
        if (key.keyId().equals(accessKey.keyId()) || key.publicJwk().equals(accessKey.publicJwk()))
            throw new IllegalStateException("Distinct access and registration signing keys required");
        // kid가 달라도 같은 키 쌍의 재사용을 거부한다.
        try {
            if (key.publicJwk().computeThumbprint().equals(accessKey.publicJwk().computeThumbprint()))
                throw new IllegalStateException("Distinct access and registration signing keys required");
        } catch (com.nimbusds.jose.JOSEException ignored) {
            throw new IllegalStateException("Invalid signing key");
        }
        return key;
    }

    @Bean
    JwtEncoder registrationJwtEncoder(@Qualifier("registrationSigningKey") JwtSigningKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(key.signingJwkSet()));
    }

    @Bean
    JwtDecoder registrationJwtDecoder(
            @Qualifier("registrationSigningKey") JwtSigningKey key,
            RegistrationJwtProperties properties,
            @Qualifier("authClock") Clock clock) {
        var processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("registration+jwt")));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                JWSAlgorithm.ES256,
                new ImmutableJWKSet<>(JwtKeyLoader.verificationKeys(
                        key, properties.verificationKeys(), "mogu.auth.registration-jwt"))));
        processor.setJWTClaimsSetVerifier((claims, context) -> {});
        var decoder = new NimbusJwtDecoder(processor);
        var converter = MappedJwtClaimSetConverter.withDefaults(Map.of());
        decoder.setClaimSetConverter(claims -> {
            if (!(claims.get("sub") instanceof String) || !validIdentity(claims) || claims.get("exp") == null)
                throw new JwtException("Invalid registration claims");
            return converter.convert(claims);
        });
        var timestamp = new JwtTimestampValidator(properties.clockSkew());
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamp, new JwtIssuerValidator(properties.issuer()), jwt -> {
                    boolean valid = validSubject(jwt.getSubject())
                            && validIdentity(jwt.getClaims())
                            && jwt.getExpiresAt() != null
                            && jwt.getAudience() != null
                            && jwt.getAudience().contains(properties.audience())
                            && "registration+jwt".equals(jwt.getHeaders().get("typ"))
                            && jwt.getHeaders().get("kid") instanceof String kid
                            && !kid.isBlank();
                    return valid
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(
                                    new OAuth2Error("invalid_token", "Invalid registration token", null));
                }));
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

    private static boolean validIdentity(Map<String, Object> claims) {
        if (!(claims.get("provider") instanceof String provider)
                || !(claims.get("provider_sub") instanceof String subject)
                || subject.isBlank()
                || subject.length() > 255) return false;
        try {
            AuthProvider.valueOf(provider);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
