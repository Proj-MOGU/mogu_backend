package com.mogu.auth.api;

import com.mogu.auth.application.LoginService;
import com.mogu.auth.domain.AuthProvider;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {
    private final LoginService service;

    public LoginController(LoginService service) {
        this.service = service;
    }

    @PostMapping("/api/auth/login/{provider}")
    public ResponseEntity<LoginResponse> login(
            @PathVariable String provider, @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(LoginResponse.from(service.login(AuthProvider.from(provider), request.idToken())));
    }
}
