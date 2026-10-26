package com.mogu.common.error;

import java.util.List;
import org.springframework.validation.BindingResult;

public record FieldErrorResponse(String field, String reason) {
    public static List<FieldErrorResponse> from(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
    }
}
