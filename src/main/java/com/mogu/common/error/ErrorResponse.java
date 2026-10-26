package com.mogu.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<FieldErrorResponse> errors, String traceId) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), null, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> errors) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), List.copyOf(errors), null);
    }

    public static ErrorResponse withTraceId(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), null, traceId);
    }
}
