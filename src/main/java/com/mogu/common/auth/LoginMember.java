package com.mogu.common.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 회원 식별자를 주입한다. 인증 예외 경로가 아니면 모든 API 는 자동으로 로그인이 필요하다.
 * <pre>{@code @GetMapping("/api/groups/mine")
 * List<GroupResponse> mine(@LoginMember MemberId memberId) { ... }}</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginMember {}
