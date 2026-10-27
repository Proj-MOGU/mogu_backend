package com.mogu.auth.domain;

import com.mogu.common.error.BusinessException;

public enum AuthProvider {
    KAKAO,
    GOOGLE;

    public static AuthProvider from(String value) {
        for (var provider : values()) if (provider.name().equalsIgnoreCase(value)) return provider;
        throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
    }
}
