package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.auth.CurrentMember;
import com.mogu.common.auth.LoginMember;
import com.mogu.common.auth.MemberId;
import com.mogu.common.error.BusinessException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {
    private final CurrentMember currentMember;

    public LoginMemberArgumentResolver(CurrentMember currentMember) {
        this.currentMember = currentMember;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMember.class) && parameter.getParameterType() == MemberId.class;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory factory) {
        return currentMember.find().orElseThrow(() -> new BusinessException(AuthErrorCode.UNAUTHORIZED));
    }
}
