package com.mogu.common.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.validation.BindingResult;

public record FieldErrorResponse(
        @Schema(description = "검증에 실패한 필드 이름", example = "idToken", requiredMode = Schema.RequiredMode.REQUIRED)
        String field,

        @Schema(
                description = "필드 검증 실패 이유",
                example = "ID Token을 입력해 주세요.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reason) {
    public static List<FieldErrorResponse> from(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
    }
}
