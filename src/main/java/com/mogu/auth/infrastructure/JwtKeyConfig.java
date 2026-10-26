package com.mogu.auth.infrastructure;

import com.mogu.common.auth.MemberId;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
        if (environment.acceptsProfiles(Profiles.of("prod")) && properties.ephemeralKeyAllowed()) {
            throw new IllegalStateException("mogu.auth.jwt.ephemeral-key-allowed");
        }
        var configured = properties.signingKey();
        boolean hasPem = configured != null && present(configured.privateKeyPem());
        boolean hasId = configured != null && present(configured.keyId());
        boolean hasPublicPem = configured != null && present(configured.publicKeyPem());
        if (hasPem != hasId || hasPem != hasPublicPem) throw signingConfigurationError();
        try {
            if (hasPem) {
                var key = (ECPrivateKey) KeyFactory.getInstance("EC")
                        .generatePrivate(new PKCS8EncodedKeySpec(pemBytes(configured.privateKeyPem(), "PRIVATE KEY")));
                requireP256(key.getParams());
                var publicKey = (ECPublicKey) KeyFactory.getInstance("EC")
                        .generatePublic(new X509EncodedKeySpec(pemBytes(configured.publicKeyPem(), "PUBLIC KEY")));
                requireP256(publicKey.getParams());
                verifyKeyPair(key, publicKey);
                return new JwtSigningKey(new ECKey.Builder(Curve.P_256, publicKey)
                        .privateKey(key)
                        .keyID(configured.keyId())
                        .build());
            }
            if (!properties.ephemeralKeyAllowed()) throw signingConfigurationError();
            LoggerFactory.getLogger(JwtKeyConfig.class).warn("임시 JWT 서명 키를 사용합니다. 재시작하면 기존 토큰은 무효가 됩니다.");
            return new JwtSigningKey(new ECKeyGenerator(Curve.P_256)
                    .keyID(UUID.randomUUID().toString())
                    .generate());
        } catch (Exception ignored) {
            throw signingConfigurationError();
        }
    }

    @Bean
    JwtEncoder jwtEncoder(JwtSigningKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(signingKey.signingJwkSet()));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtSigningKey signingKey, JwtProperties properties, @Qualifier("authClock") Clock clock) {
        List<JWK> keys = new ArrayList<>();
        keys.add(signingKey.publicJwk());
        Set<String> ids = new HashSet<>();
        ids.add(signingKey.keyId());
        for (var verification : properties.verificationKeys()) {
            try {
                if (!present(verification.keyId()) || !ids.add(verification.keyId()))
                    throw new IllegalArgumentException();
                var key = (ECPublicKey) KeyFactory.getInstance("EC")
                        .generatePublic(new X509EncodedKeySpec(pemBytes(verification.publicKeyPem(), "PUBLIC KEY")));
                requireP256(key.getParams());
                keys.add(new ECKey.Builder(Curve.P_256, key)
                        .keyID(verification.keyId())
                        .build());
            } catch (Exception ignored) {
                throw new IllegalStateException("mogu.auth.jwt.verification-keys");
            }
        }
        return createDecoder(new JWKSet(keys), properties, clock);
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

    private static void verifyKeyPair(ECPrivateKey privateKey, ECPublicKey publicKey) throws GeneralSecurityException {
        byte[] challenge = new byte[32];
        new SecureRandom().nextBytes(challenge);
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(challenge);
        byte[] signed = signature.sign();
        signature.initVerify(publicKey);
        signature.update(challenge);
        if (!signature.verify(signed)) throw signingConfigurationError();
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

    private static byte[] pemBytes(String pem, String label) {
        String start = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        String trimmed = pem.strip();
        if (!trimmed.startsWith(start) || !trimmed.endsWith(end)) throw new IllegalArgumentException();
        return Base64.getDecoder()
                .decode(trimmed.substring(start.length(), trimmed.length() - end.length())
                        .replaceAll("\\s", ""));
    }

    private static void requireP256(ECParameterSpec parameters) {
        if (!Curve.P_256.equals(Curve.forECParameterSpec(parameters))) throw new IllegalArgumentException();
    }

    private static IllegalStateException signingConfigurationError() {
        return new IllegalStateException("mogu.auth.jwt.signing-key.key-id, mogu.auth.jwt.signing-key.private-key-pem,"
                + " mogu.auth.jwt.signing-key.public-key-pem, mogu.auth.jwt.ephemeral-key-allowed");
    }
}
