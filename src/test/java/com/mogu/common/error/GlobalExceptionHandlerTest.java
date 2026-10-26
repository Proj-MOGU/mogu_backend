package com.mogu.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.infrastructure.JsonAuthenticationEntryPoint;
import com.mogu.auth.infrastructure.JwtKeyConfig;
import com.mogu.auth.infrastructure.MemberAuthenticationConverter;
import com.mogu.auth.infrastructure.SecurityConfig;
import com.mogu.common.trace.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("test")
@WebMvcTest(GlobalExceptionHandlerTest.DummyController.class)
@Import({
    SecurityConfig.class,
    JwtKeyConfig.class,
    MemberAuthenticationConverter.class,
    JsonAuthenticationEntryPoint.class,
    TraceIdFilter.class,
    GlobalExceptionHandler.class,
    GlobalExceptionHandlerTest.DummyController.class
})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {
    private final MockMvc mockMvc;

    @Autowired
    GlobalExceptionHandlerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("비즈니스 예외는 도메인 코드와 고정 메시지만 반환한다")
    void businessExceptionReturnsDomainError() throws Exception {
        // given
        var request = get("/test/errors/business").with(user("tester"));
        // when
        var result = mockMvc.perform(request);
        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_CONFLICT"))
                .andExpect(jsonPath("$.message").value("이미 존재합니다."))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    @DisplayName("본문 검증 실패는 필드와 메시지를 반환하고 거절값을 노출하지 않는다")
    void invalidBodyReturnsFieldErrorsWithoutRejectedValue() throws Exception {
        // given
        var request = post("/test/errors/body")
                .with(user("tester"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"SECRET-REJECTED-VALUE\"}");
        // when
        var result = mockMvc.perform(request);
        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].reason").value("비밀번호는 5자 이하여야 합니다."))
                .andExpect(response -> assertThat(response.getResponse().getContentAsString())
                        .doesNotContain("SECRET-REJECTED-VALUE", "rejectedValue"));
    }

    @Test
    @DisplayName("매개변수 검증 실패는 매개변수 이름과 기본 메시지를 반환한다")
    void invalidParameterReturnsFieldErrors() throws Exception {
        // given
        var request = get("/test/errors/parameter").param("count", "0").with(user("tester"));
        // when
        var result = mockMvc.perform(request);
        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("count"))
                .andExpect(jsonPath("$.errors[0].reason").value("수량은 1 이상이어야 합니다."));
    }

    @Test
    @DisplayName("깨진 JSON은 필드 오류 없이 입력 오류를 반환한다")
    void malformedJsonReturnsInvalidInput() throws Exception {
        // given
        var request = post("/test/errors/body")
                .with(user("tester"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{broken");
        // when
        var result = mockMvc.perform(request);
        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("경로 변수 타입이 다르면 입력 오류를 반환한다")
    void mismatchedPathVariableReturnsInvalidInput() throws Exception {
        // given
        var request = get("/test/errors/items/not-a-number").with(user("tester"));
        // when
        var result = mockMvc.perform(request);
        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("필수 매개변수가 없으면 입력 오류를 반환한다")
    void missingParameterReturnsInvalidInput() throws Exception {
        // given
        var request = get("/test/errors/parameter").with(user("tester"));
        // when
        var result = mockMvc.perform(request);
        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("없는 리소스는 공통 404 오류를 반환한다")
    void missingResourceReturnsNotFound() throws Exception {
        // given
        var request = get("/test/errors/missing-resource").with(user("tester"));
        // when
        var result = mockMvc.perform(request);
        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 공통 405 오류를 반환한다")
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        // given
        var request = post("/test/errors/business").with(user("tester"));
        // when
        var result = mockMvc.perform(request);
        // then
        result.andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식입니다."));
    }

    @Test
    @DisplayName("예상하지 못한 오류는 내부 정보를 숨기고 응답과 로그에 같은 추적 ID를 남긴다")
    void unexpectedExceptionReturnsTraceIdAndLogsOriginalException(CapturedOutput output) throws Exception {
        // given
        var request = get("/test/errors/unexpected").with(user("tester"));
        // when
        var result = mockMvc.perform(request);
        // then
        var response = result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn()
                .getResponse();
        String traceId = response.getHeader("X-Trace-Id");
        assertThat(traceId).matches("[0-9a-f]{32}");
        result.andExpect(jsonPath("$.traceId").value(traceId));
        assertThat(response.getContentAsString()).doesNotContain("SECRET-INTERNAL-DETAIL");
        assertThat(output.getAll())
                .contains(
                        "ERROR",
                        traceId,
                        "java.lang.IllegalStateException",
                        "SECRET-INTERNAL-DETAIL",
                        "at com.mogu.common.error.GlobalExceptionHandlerTest$DummyController");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("클라이언트 추적 ID를 무시하고 요청마다 새 추적 ID를 생성한다")
    void clientTraceIdIsIgnoredAndEachRequestGetsNewTraceId() throws Exception {
        // given
        var request = get("/test/errors/unexpected").with(user("tester")).header("X-Trace-Id", "attacker-value");
        // when
        var first = mockMvc.perform(request);
        var second = mockMvc.perform(request);
        // then
        String firstTraceId = first.andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getHeader("X-Trace-Id");
        String secondTraceId = second.andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getHeader("X-Trace-Id");
        assertThat(firstTraceId).matches("[0-9a-f]{32}").isNotEqualTo("attacker-value");
        assertThat(secondTraceId).matches("[0-9a-f]{32}").isNotEqualTo(firstTraceId);
        first.andExpect(jsonPath("$.traceId").value(firstTraceId));
        second.andExpect(jsonPath("$.traceId").value(secondTraceId));
    }

    @Test
    @DisplayName("보안 필터가 거부한 미인증 요청에도 추적 헤더가 있고 MDC가 정리된다")
    void unauthorizedResponseIncludesTraceHeader() throws Exception {
        // given
        var request = get("/test/errors/business");
        // when
        var result = mockMvc.perform(request);
        // then
        String traceId = result.andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getHeader("X-Trace-Id");
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    enum TestErrorCode implements ErrorCode {
        CONFLICT;

        @Override
        public String code() {
            return "TEST_CONFLICT";
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String message() {
            return "이미 존재합니다.";
        }
    }

    @RestController
    static class DummyController {
        @GetMapping("/test/errors/business")
        String business() {
            throw new BusinessException(TestErrorCode.CONFLICT);
        }

        @PostMapping("/test/errors/body")
        void body(@Valid @RequestBody TestRequest request) {}

        @GetMapping("/test/errors/parameter")
        void parameter(@RequestParam @Min(value = 1, message = "수량은 1 이상이어야 합니다.") int count) {}

        @GetMapping("/test/errors/items/{id}")
        void item(@PathVariable long id) {}

        @GetMapping("/test/errors/unexpected")
        String unexpected() {
            throw new IllegalStateException("SECRET-INTERNAL-DETAIL");
        }
    }

    record TestRequest(
            @Size(max = 5, message = "비밀번호는 5자 이하여야 합니다.") String password) {}
}
