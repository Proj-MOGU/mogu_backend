package com.mogu.member.domain;

import com.mogu.common.error.BusinessException;
import java.text.Normalizer;

public record Bio(String value) {
    /** 저장된 값을 정규화나 입력 정책 검사 없이 복원한다. */
    public static Bio reconstitute(String value) {
        return new Bio(value);
    }

    public static Bio of(String value) {
        if (value != null) {
            value = Normalizer.normalize(value, Normalizer.Form.NFC).strip();
            if (value.codePointCount(0, value.length()) > 50
                    || value.codePoints().anyMatch(Bio::isForbidden))
                throw new BusinessException(MemberErrorCode.INVALID_BIO);
            if (value.isEmpty()) value = null;
        }
        return new Bio(value);
    }

    private static boolean isForbidden(int point) {
        int type = Character.getType(point);
        return type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
