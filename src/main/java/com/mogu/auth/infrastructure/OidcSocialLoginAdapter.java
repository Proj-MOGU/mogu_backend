package com.mogu.auth.infrastructure;

import com.mogu.auth.application.SocialLoginAdapter;
import com.mogu.auth.application.VerifiedSocialIdentity;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.common.error.BusinessException;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.RateLimitReachedException;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public abstract class OidcSocialLoginAdapter implements SocialLoginAdapter {
    private static final Logger log = LoggerFactory.getLogger(OidcSocialLoginAdapter.class);
    private final AuthProvider provider;
    private final NimbusJwtDecoder decoder;

    protected OidcSocialLoginAdapter(
            AuthProvider provider, OidcProviderProperties.Provider properties, Clock clock, Environment environment) {
        this.provider = provider;
        if (properties.audiences().isEmpty()) {
            if (environment.acceptsProfiles(Profiles.of("prod")))
                throw new IllegalStateException(provider + " audiences required");
            log.warn("{} 로그인이 비활성화되었습니다", provider);
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(factory))
                .build();
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
            return new VerifiedSocialIdentity(provider, jwt.getSubject(), jwt.getClaimAsString("nonce"));
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
