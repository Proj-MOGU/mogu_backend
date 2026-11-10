package com.mogu.auth.infrastructure;

import com.mogu.common.auth.CurrentMember;
import com.mogu.common.auth.MemberId;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityContextCurrentMember implements CurrentMember {
    @Override
    public Optional<MemberId> find() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof MemberAuthentication member && member.isAuthenticated()) {
            return Optional.of(member.getPrincipal());
        }
        return Optional.empty();
    }
}
