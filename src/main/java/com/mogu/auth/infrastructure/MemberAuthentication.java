package com.mogu.auth.infrastructure;

import com.mogu.common.auth.MemberId;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class MemberAuthentication extends AbstractAuthenticationToken {
    private final MemberId memberId;

    public MemberAuthentication(MemberId memberId) {
        super(List.of());
        this.memberId = Objects.requireNonNull(memberId);
        super.setAuthenticated(true);
    }

    @Override
    public MemberId getPrincipal() {
        return memberId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
