package com.mogu.auth.api;

import com.mogu.auth.application.LogoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogoutController {
    private final LogoutService service;

    public LogoutController(LogoutService service) {
        this.service = service;
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        service.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> malformedBody() {
        return ResponseEntity.noContent().build();
    }
}
