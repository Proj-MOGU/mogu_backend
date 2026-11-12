package com.mogu.auth.infrastructure;

import com.mogu.auth.application.RateLimiter;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * DB 없는 슬라이스에서는 RateLimiter 빈이 없으면 횟수 제한 필터를 등록하지 않는다.
 * DataSource 빈이 있는 환경에서는 RateLimiter 누락으로 제한이 조용히 꺼지지 않도록 기동에 실패한다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    private final ObjectProvider<RateLimiter> limiter;
    private final JsonMapper mapper;

    public SecurityConfig(ObjectProvider<RateLimiter> limiter, JsonMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Bean
    SmartInitializingSingleton requireRateLimiterWithDataSource(ObjectProvider<DataSource> dataSources) {
        return () -> {
            if (dataSources.stream().findAny().isPresent() && limiter.getIfAvailable() == null) {
                throw new IllegalStateException(
                        "DataSource bean exists but RateLimiter bean is missing; authentication rate limiting"
                                + " must be configured");
            }
        };
    }

    @Bean
    DefaultBearerTokenResolver bearerTokenResolver() {
        var resolver = new DefaultBearerTokenResolver();
        resolver.setAllowUriQueryParameter(false);
        resolver.setAllowFormEncodedBodyParameter(false);
        return resolver;
    }

    private HttpSecurity common(HttpSecurity http, JsonAuthenticationEntryPoint entryPoint) throws Exception {
        return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint));
    }

    @Bean
    @Order(1)
    SecurityFilterChain registrationSecurityFilterChain(
            HttpSecurity http,
            PendingRegistrationAuthenticationConverter converter,
            JsonAuthenticationEntryPoint entryPoint,
            DefaultBearerTokenResolver resolver,
            @Qualifier("registrationJwtDecoder") JwtDecoder decoder)
            throws Exception {
        limiter.ifAvailable(rateLimiter -> http.addFilterAfter(
                new AuthRateLimitFilter(rateLimiter, mapper), BearerTokenAuthenticationFilter.class));
        return common(http, entryPoint)
                .securityMatcher("/api/auth/identity-verification", "/api/auth/mock-identity-verifications")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .bearerTokenResolver(resolver))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            MemberAuthenticationConverter converter,
            JsonAuthenticationEntryPoint entryPoint,
            DefaultBearerTokenResolver resolver,
            @Qualifier("jwtDecoder") JwtDecoder decoder,
            Environment environment)
            throws Exception {
        limiter.ifAvailable(rateLimiter -> http.addFilterBefore(
                new AuthRateLimitFilter(rateLimiter, mapper), BearerTokenAuthenticationFilter.class));
        return common(http, entryPoint)
                .authorizeHttpRequests(a -> {
                    if (environment.acceptsProfiles(Profiles.of("local", "dev"))) {
                        a.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                                .permitAll();
                    }
                    a.requestMatchers(
                                    HttpMethod.POST,
                                    "/api/auth/nonce",
                                    "/api/auth/login/*",
                                    "/api/auth/refresh",
                                    "/api/auth/logout")
                            .permitAll()
                            .requestMatchers("/actuator/health")
                            .permitAll()
                            .anyRequest()
                            .authenticated();
                })
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .bearerTokenResolver(resolver))
                .build();
    }
}
