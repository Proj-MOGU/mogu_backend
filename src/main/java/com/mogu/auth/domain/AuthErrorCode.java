package com.mogu.auth.domain;

import com.mogu.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
    INVALID_IDENTITY_VERIFICATION("AUTH_INVALID_IDENTITY_VERIFICATION", HttpStatus.BAD_REQUEST, "본인인증 정보를 확인할 수 없습니다."),
    PROVIDER_ALREADY_LINKED("AUTH_PROVIDER_ALREADY_LINKED", HttpStatus.CONFLICT, "이미 같은 로그인 방식의 다른 계정이 연결되어 있습니다."),
    ACCOUNT_SANCTIONED("AUTH_ACCOUNT_SANCTIONED", HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),
    UNAUTHORIZED("AUTH_UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    UNSUPPORTED_PROVIDER("AUTH_UNSUPPORTED_PROVIDER", HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다."),
    INVALID_SOCIAL_TOKEN("AUTH_INVALID_SOCIAL_TOKEN", HttpStatus.UNAUTHORIZED, "소셜 로그인 정보를 확인할 수 없습니다."),
    PROVIDER_UNAVAILABLE("AUTH_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "소셜 로그인 서비스에 일시적으로 연결할 수 없습니다.");
    private final String code;
    private final HttpStatus status;
    private final String message;

    AuthErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
