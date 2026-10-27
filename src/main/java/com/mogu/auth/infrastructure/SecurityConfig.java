package com.mogu.auth.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
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
            @Qualifier("jwtDecoder") JwtDecoder decoder)
            throws Exception {
        return common(http, entryPoint)
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.POST, "/api/auth/nonce", "/api/auth/login/*")
                        .permitAll()
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .bearerTokenResolver(resolver))
                .build();
    }
}
