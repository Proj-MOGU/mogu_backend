package com.mogu.member.domain;

import com.mogu.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MemberErrorCode implements ErrorCode {
    PROFILE_NOT_FOUND("MEMBER_PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND, "회원 프로필을 찾을 수 없습니다."),
    INVALID_NICKNAME("MEMBER_INVALID_NICKNAME", HttpStatus.BAD_REQUEST, "닉네임은 2~12자의 한글, 영문, 숫자만 쓸 수 있습니다."),
    RESERVED_NICKNAME("MEMBER_RESERVED_NICKNAME", HttpStatus.BAD_REQUEST, "사용할 수 없는 닉네임입니다."),
    DUPLICATE_NICKNAME("MEMBER_DUPLICATE_NICKNAME", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    NICKNAME_CHANGE_TOO_SOON("MEMBER_NICKNAME_CHANGE_TOO_SOON", HttpStatus.CONFLICT, "닉네임은 30일에 한 번만 바꿀 수 있습니다."),
    INVALID_BIO("MEMBER_INVALID_BIO", HttpStatus.BAD_REQUEST, "한줄소개는 줄바꿈 없이 50자까지 쓸 수 있습니다.");
    private final String code;
    private final HttpStatus status;
    private final String message;

    MemberErrorCode(String code, HttpStatus status, String message) {
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
