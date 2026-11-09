package com.mogu.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @Schema(description = "오류 코드", example = "COMMON_INVALID_INPUT", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Schema(description = "오류 안내 메시지", example = "입력값이 올바르지 않습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @Schema(
                description = "입력 검증 실패 시 필드별 오류. 없으면 생략",
                example = "[{\"field\":\"idToken\",\"reason\":\"ID Token을 입력해 주세요.\"}]",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<FieldErrorResponse> errors,

        @Schema(
                description = "서버 내부 오류 추적 식별자. 그 외에는 생략",
                example = "trace-id-placeholder",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String traceId) {
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
