package com.mogu.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {
    @Test
    @DisplayName("필드 오류 목록은 원본 변경의 영향을 받지 않으며 외부에서 수정할 수 없다")
    void fieldErrorsAreDefensivelyCopied() {
        // given
        var fieldError = new FieldErrorResponse("name", "이름을 입력해 주세요.");
        var errors = new ArrayList<FieldErrorResponse>();
        errors.add(fieldError);
        // when
        var response = ErrorResponse.of(CommonErrorCode.INVALID_INPUT, errors);
        errors.clear();
        // then
        assertThat(response.errors()).containsExactly(fieldError);
        assertThatThrownBy(() -> response.errors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
