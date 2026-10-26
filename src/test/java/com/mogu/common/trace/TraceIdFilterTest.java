package com.mogu.common.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {
    @Test
    @DisplayName("후속 필터가 예외를 던져도 추적 ID를 MDC에서 제거한다")
    void removesTraceIdWhenFilterChainThrows() {
        // given
        var filter = new TraceIdFilter();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var failure = new ServletException("filter failure");
        // when
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                    assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isEqualTo(response.getHeader("X-Trace-Id"));
                    throw failure;
                }))
                .isSameAs(failure);
        // then
        assertThat(response.getHeader("X-Trace-Id")).matches("[0-9a-f]{32}");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }
}
