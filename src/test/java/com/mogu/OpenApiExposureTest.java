package com.mogu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mogu.auth.application.PiiCipher;
import com.mogu.auth.infrastructure.JsonAuthenticationEntryPoint;
import com.mogu.auth.infrastructure.MemberAuthenticationConverter;
import com.mogu.auth.infrastructure.PendingRegistrationAuthenticationConverter;
import com.mogu.auth.infrastructure.SecurityConfig;
import com.mogu.common.config.OpenApiConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

class OpenApiExposureTest {
    @Test
    @DisplayName("prod 프로파일에서는 API 문서와 Swagger UI가 꺼진다")
    void prodDisablesDocumentation() {
        assertDisabled("prod");
    }

    @Test
    @DisplayName("기본 프로파일에서는 API 문서와 Swagger UI가 꺼진다")
    void defaultDisablesDocumentation() {
        assertDisabled("default");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev"})
    @DisplayName("local과 dev 프로파일에서는 인증 없이 API 문서와 Swagger UI가 열린다")
    void localAndDevExposeDocumentation(String profile) {
        runner(profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SpringDocConfigProperties.class)
                            .getApiDocs()
                            .isEnabled())
                    .isTrue();
            assertThat(context.getBean(SwaggerUiConfigProperties.class).isEnabled())
                    .isTrue();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(springSecurity())
                    .build();
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
            mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
            // swagger-ui.html은 실제 구현에서 index.html로 리다이렉트한다.
            var response = mvc.perform(get("/swagger-ui.html"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn()
                    .getResponse();
            assertThat(response.getRedirectedUrl()).isEqualTo("/swagger-ui/index.html");
            mvc.perform(get(response.getRedirectedUrl())).andExpect(status().isOk());
        });
    }

    private void assertDisabled(String profile) {
        runner(profile).run(context -> {
            assertThat(context).hasNotFailed();
            for (String property : List.of("springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled")) {
                assertThat(context.getEnvironment().getProperty(property, Boolean.class))
                        .isFalse();
            }
            assertThat(context).doesNotHaveBean(SpringDocWebMvcConfiguration.class);
            assertThat(context).doesNotHaveBean(SwaggerConfig.class);
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(springSecurity())
                    .build();
            for (String path : List.of(
                    "/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html")) {
                mvc.perform(get(path)).andExpect(status().isUnauthorized());
            }
        });
    }

    private WebApplicationContextRunner runner(String profile) {
        return new WebApplicationContextRunner()
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles(profile);
                    var loader = new YamlPropertySourceLoader();
                    for (String file : List.of("application.yml", "application-" + profile + ".yml")) {
                        var resource = new ClassPathResource(file);
                        if (resource.exists()) {
                            try {
                                for (var source : loader.load(file, resource)) {
                                    context.getEnvironment()
                                            .getPropertySources()
                                            .addFirst(source);
                                }
                            } catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        }
                    }
                })
                .withConfiguration(AutoConfigurations.of(
                        SpringDocConfiguration.class,
                        SpringDocConfigProperties.class,
                        SpringDocWebMvcConfiguration.class,
                        SwaggerConfig.class,
                        SwaggerUiConfigProperties.class,
                        SwaggerUiOAuthProperties.class))
                .withUserConfiguration(WebConfiguration.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
        SecurityConfig.class,
        OpenApiConfig.class,
        MemberAuthenticationConverter.class,
        JsonAuthenticationEntryPoint.class
    })
    static class WebConfiguration {
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        // 인증 없는 문서 요청만 검증한다. 실제 서명 키나 DB가 필요하지 않다.
        @Bean(name = {"jwtDecoder", "registrationJwtDecoder"})
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new BadJwtException("No token accepted in documentation exposure test");
            };
        }

        @Bean
        PendingRegistrationAuthenticationConverter registrationConverter() {
            return new PendingRegistrationAuthenticationConverter(new PiiCipher() {
                @Override
                public String encrypt(String plaintext, String context) {
                    throw new UnsupportedOperationException("Not used by documentation requests");
                }

                @Override
                public String decrypt(String ciphertext, String context) {
                    throw new UnsupportedOperationException("Not used by documentation requests");
                }
            });
        }
    }
}
