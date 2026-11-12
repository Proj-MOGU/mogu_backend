package com.mogu.auth.infrastructure;

import com.mogu.auth.application.SocialLoginAdapter;
import com.mogu.auth.application.VerifiedSocialIdentity;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.common.error.BusinessException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClientException;

public abstract class OidcSocialLoginAdapter implements SocialLoginAdapter {
    private static final Logger log = LoggerFactory.getLogger(OidcSocialLoginAdapter.class);
    private final AuthProvider provider;
    private final NimbusJwtDecoder decoder;

    protected OidcSocialLoginAdapter(
            AuthProvider provider,
            OidcProviderProperties.Provider properties,
            Duration refreshMinInterval,
            Clock clock,
            Environment environment) {
        this.provider = provider;
        if (properties.audiences().isEmpty()) {
            if (environment.acceptsProfiles(Profiles.of("prod")))
                throw new IllegalStateException(provider + " audiences required");
            log.warn("{} 로그인이 비활성화되었습니다", provider);
        }
        try {
            var retriever = new DefaultResourceRetriever(
                    Math.toIntExact(properties.connectTimeout().toMillis()),
                    Math.toIntExact(properties.readTimeout().toMillis()));
            var source = JWKSourceBuilder.<SecurityContext>create(
                            properties.jwkSetUri().toURL(), retriever)
                    .cache(true)
                    .rateLimited(refreshMinInterval.toMillis())
                    .outageTolerant(false)
                    .retrying(false)
                    .build();
            var processor = new DefaultJWTProcessor<SecurityContext>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source));
            // 클레임 검증은 아래 Spring 검증기가 authClock으로 수행한다.
            processor.setJWTClaimsSetVerifier((claims, context) -> {});
            decoder = new NimbusJwtDecoder(processor);
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("Invalid JWKS URL", exception);
        }
        // Spring 기본 변환기가 누락된 iat를 보완하기 전에 필수 클레임의 존재를 확인한다.
        var converter = MappedJwtClaimSetConverter.withDefaults(Map.of());
        decoder.setClaimSetConverter(claims -> {
            if (!nonBlankString(claims.get("sub"))
                    || !nonBlankString(claims.get("nonce"))
                    || claims.get("exp") == null
                    || claims.get("iat") == null) throw new JwtException("Invalid required claims");
            return converter.convert(claims);
        });
        var timestamp = new JwtTimestampValidator(Duration.ofSeconds(60));
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, jwt -> {
            boolean valid = jwt.getClaimAsString("iss") != null
                    && properties.issuers().contains(jwt.getClaimAsString("iss"))
                    && jwt.getAudience() != null
                    && jwt.getAudience().stream().anyMatch(properties.audiences()::contains)
                    && jwt.getExpiresAt() != null
                    && jwt.getIssuedAt() != null
                    && nonBlankString(jwt.getClaims().get("sub"))
                    && nonBlankString(jwt.getClaims().get("nonce"));
            return valid
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Invalid social token claims", null));
        }));
    }

    @Override
    public final AuthProvider provider() {
        return provider;
    }

    @Override
    public final VerifiedSocialIdentity verify(String idToken) {
        if (idToken == null || idToken.isBlank()) throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        try {
            var jwt = decoder.decode(idToken);
            // Nimbus가 숫자 sub를 문자열로 변환하므로, 서명 검증 이후 원본 JSON 타입도 확인한다.
            var rawClaims = SignedJWT.parse(idToken).getPayload().toJSONObject();
            if (rawClaims == null || !nonBlankString(rawClaims.get("sub")) || !nonBlankString(rawClaims.get("nonce")))
                throw new JwtException("Invalid required claim types");
            Object rawEmail = rawClaims.get("email");
            String email = rawEmail instanceof String text && text.length() <= 320 && text.contains("@") ? text : null;
            Object verified = rawClaims.get("email_verified");
            return new VerifiedSocialIdentity(
                    provider,
                    jwt.getSubject(),
                    jwt.getClaimAsString("nonce"),
                    email,
                    email != null && (Boolean.TRUE.equals(verified) || "true".equals(verified)));
        } catch (JwtException | ParseException exception) {
            boolean unavailable = unavailable(exception);
            log.debug("{} {}", provider, unavailable ? "PROVIDER_UNAVAILABLE" : "INVALID_SOCIAL_TOKEN");
            throw new BusinessException(
                    unavailable ? AuthErrorCode.PROVIDER_UNAVAILABLE : AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    private static boolean nonBlankString(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    static boolean unavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RateLimitReachedException) return false;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof KeySourceException || cause instanceof RestClientException) return true;
        }
        return false;
    }
}
