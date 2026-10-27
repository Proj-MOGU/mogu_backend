package com.mogu.auth.infrastructure;

import com.mogu.auth.api.PendingRegistration;
import com.mogu.auth.api.PendingSocialIdentity;
import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.error.BusinessException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class PendingRegistrationArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(PendingRegistration.class)
                && parameter.getParameterType() == PendingSocialIdentity.class;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory factory) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PendingSocialIdentity identity)
            return identity;
        throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
    }
}
