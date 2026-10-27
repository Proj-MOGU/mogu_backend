package com.mogu.auth.infrastructure;

import com.mogu.auth.api.PendingSocialIdentity;
import com.mogu.auth.domain.AuthProvider;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class PendingRegistrationAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new PendingRegistrationAuthentication(new PendingSocialIdentity(
                UUID.fromString(jwt.getSubject()),
                AuthProvider.valueOf(jwt.getClaimAsString("provider")),
                jwt.getClaimAsString("provider_sub")));
    }
}
