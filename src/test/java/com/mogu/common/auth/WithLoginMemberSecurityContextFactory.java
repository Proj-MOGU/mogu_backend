package com.mogu.common.auth;

import com.mogu.auth.infrastructure.MemberAuthentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/** {@code @WithLoginMember("00000000-0000-7000-8000-000000000001")}의 인증 컨텍스트를 생성한다. */
public class WithLoginMemberSecurityContextFactory implements WithSecurityContextFactory<WithLoginMember> {
    @Override
    public SecurityContext createSecurityContext(WithLoginMember annotation) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new MemberAuthentication(MemberId.from(annotation.value())));
        return context;
    }
}
