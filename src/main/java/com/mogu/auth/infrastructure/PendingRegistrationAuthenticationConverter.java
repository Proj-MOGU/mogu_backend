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
    private final com.mogu.auth.application.PiiCipher cipher;

    public PendingRegistrationAuthenticationConverter(com.mogu.auth.application.PiiCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String email = null;
        String encrypted = jwt.getClaimAsString("email_enc");
        if (encrypted != null) {
            try {
                email = cipher.decrypt(
                        encrypted,
                        AuthAccountMapper.emailContext(
                                jwt.getClaimAsString("provider"), jwt.getClaimAsString("provider_sub")));
            } catch (IllegalStateException ignored) {
                throw new org.springframework.security.authentication.BadCredentialsException(
                        "Invalid registration token");
            }
        }
        return new PendingRegistrationAuthentication(new PendingSocialIdentity(
                UUID.fromString(jwt.getSubject()),
                AuthProvider.valueOf(jwt.getClaimAsString("provider")),
                jwt.getClaimAsString("provider_sub"),
                email,
                email != null && Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))));
    }
}
