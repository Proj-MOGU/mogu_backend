package com.mogu.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * 회원 인증이 필요한 MVC 테스트에 사용한다.
 * <pre>{@code @Test @WithLoginMember("00000000-0000-7000-8000-000000000001")
 * void findsMyGroups() { ... }}</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WithSecurityContext(factory = WithLoginMemberSecurityContextFactory.class)
public @interface WithLoginMember {
    String value() default "00000000-0000-7000-8000-000000000001";
}
