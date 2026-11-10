package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.mogu.auth.api.PendingSocialIdentity;
import com.mogu.auth.domain.AuthProvider;
import com.mogu.common.auth.MemberId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextCurrentMemberTest {
    private final SecurityContextCurrentMember currentMember = new SecurityContextCurrentMember();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 회원의 식별자를 반환한다")
    void findsAuthenticatedMember() {
        // given
        var id = new MemberId(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(new MemberAuthentication(id));
        // when
        var result = currentMember.find();
        // then
        assertThat(result).contains(id);
    }

    @Test
    @DisplayName("회원 인증 객체여도 인증되지 않았으면 빈 값을 반환한다")
    void rejectsUnauthenticatedMember() {
        // given
        var authentication = new MemberAuthentication(new MemberId(UUID.randomUUID()));
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // when
        var result = currentMember.find();
        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("가입 대기 인증은 빈 값을 반환한다")
    void rejectsPendingRegistration() {
        // given
        var identity = new PendingSocialIdentity(UUID.randomUUID(), AuthProvider.KAKAO, "subject");
        SecurityContextHolder.getContext().setAuthentication(new PendingRegistrationAuthentication(identity));
        // when
        var result = currentMember.find();
        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("익명 인증은 빈 값을 반환한다")
    void rejectsAnonymousAuthentication() {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "test-key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        // when
        var result = currentMember.find();
        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("인증이 없으면 빈 값을 반환한다")
    void returnsEmptyWithoutAuthentication() {
        // given
        SecurityContextHolder.clearContext();
        // when
        var result = currentMember.find();
        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("회원 식별자를 가진 다른 인증 구현은 인증된 상태여도 빈 값을 반환한다")
    void rejectsOtherAuthenticationWithMemberPrincipal() {
        // given
        var authentication = new TestingAuthenticationToken(new MemberId(UUID.randomUUID()), null);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // when
        var result = currentMember.find();
        // then
        assertThat(result).isEmpty();
    }
}
