package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AuthErrorCode;
import com.mogu.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);
    private final JsonMapper mapper;

    public JsonAuthenticationEntryPoint(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.debug("{}: {}", exception.getClass().getSimpleName(), safeMessage(request, exception));
        response.setStatus(401);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(AuthErrorCode.UNAUTHORIZED));
    }

    private static String safeMessage(HttpServletRequest request, AuthenticationException exception) {
        String message = exception.getMessage();
        if (message == null) return "Authentication failed";
        // 파서 예외가 입력을 인용해도 Authorization 값과 토큰 원문은 기록하지 않는다.
        var headers = request.getHeaders("Authorization");
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            if (!header.isBlank()) message = message.replace(header, "***");
            int separator = header.indexOf(' ');
            String credential =
                    separator < 0 ? header : header.substring(separator + 1).strip();
            if (!credential.isEmpty()) message = message.replace(credential, "***");
        }
        return message.replaceAll("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*", "***")
                .replaceAll("[\\r\\n]", " ");
    }
}
