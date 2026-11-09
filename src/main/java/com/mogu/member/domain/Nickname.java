package com.mogu.member.domain;

import com.mogu.common.error.BusinessException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public record Nickname(String value) {
    private static final List<String> RESERVED_WORDS =
            List.of("운영자", "관리자", "운영팀", "고객센터", "모구", "admin", "mogu", "official");

    public Nickname {
        if (value == null) throw new BusinessException(MemberErrorCode.INVALID_NICKNAME);
    }

    /** 저장된 값을 정규화나 입력 정책 검사 없이 복원한다. */
    public static Nickname reconstitute(String value) {
        return new Nickname(value);
    }

    public static Nickname of(String value) {
        if (value == null) throw new BusinessException(MemberErrorCode.INVALID_NICKNAME);
        value = Normalizer.normalize(value, Normalizer.Form.NFC).strip();
        if (!value.matches("[가-힣A-Za-z0-9]{2,12}")) throw new BusinessException(MemberErrorCode.INVALID_NICKNAME);
        String key = value.toLowerCase(Locale.ROOT);
        if (RESERVED_WORDS.stream().anyMatch(key::contains))
            throw new BusinessException(MemberErrorCode.RESERVED_NICKNAME);
        return new Nickname(value);
    }

    public String key() {
        return value.toLowerCase(Locale.ROOT);
    }
}
