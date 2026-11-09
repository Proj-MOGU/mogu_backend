package com.mogu.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("MOGU API").version("v1").description("MOGU 서비스의 API 계약입니다."))
                .addServersItem(new Server().url("/"))
                .components(new Components()
                        .addSecuritySchemes("accessToken", bearer("로그인 후 받은 Access Token"))
                        .addSecuritySchemes(
                                "registrationToken", bearer("소셜 로그인 후 가입 대기 시 받은 Registration Token. 본인인증 API 전용")))
                .addSecurityItem(new SecurityRequirement().addList("accessToken"));
    }

    private SecurityScheme bearer(String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(description);
    }
}
