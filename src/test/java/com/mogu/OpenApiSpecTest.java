package com.mogu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@Testcontainers
class OpenApiSpecTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    private final MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Autowired
    OpenApiSpecTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @DisplayName("API 스펙을 openapi/openapi.json 으로 저장한다")
    void writesDeterministicOpenApiSpec() throws Exception {
        // given / when
        byte[] first = generateSpec();
        byte[] second = generateSpec();
        // then
        assertThat(second).isEqualTo(first);
        JsonNode spec = json.readTree(first);
        assertThat(spec.path("paths").propertyNames())
                .allSatisfy(path -> assertThat(path).startsWith("/api/"));
        for (String path : List.of("nonce", "login/{provider}", "refresh", "logout")) {
            JsonNode operation = spec.path("paths").path("/api/auth/" + path).path("post");
            assertThat(operation.isMissingNode()).as(path).isFalse();
            assertThat(operation.path("security").isArray()).as(path).isTrue();
            assertThat(operation.path("security").size()).as(path).isZero();
        }
        for (String path : List.of("mock-identity-verifications", "identity-verification")) {
            assertThat(spec.path("paths").path("/api/auth/" + path).path("post").path("security"))
                    .isEqualTo(json.readTree("[{\"registrationToken\":[]}]"));
        }
        for (String path : List.of(
                "nonce",
                "login/{provider}",
                "refresh",
                "logout",
                "identity-verification",
                "mock-identity-verifications")) {
            var limited = spec.path("paths")
                    .path("/api/auth/" + path)
                    .path("post")
                    .path("responses")
                    .path("429");
            assertThat(limited.path("description").asText()).contains("AUTH_TOO_MANY_REQUESTS");
            assertThat(limited.path("headers").has("Retry-After")).isTrue();
        }
        assertThat(spec.path("security")).isEqualTo(json.readTree("[{\"accessToken\":[]}]"));
        JsonNode account = spec.path("paths").path("/api/auth/me/account").path("get");
        assertThat(account.isMissingNode()).isFalse();
        assertThat(account.has("security") ? account.path("security") : spec.path("security"))
                .isEqualTo(json.readTree("[{\"accessToken\":[]}]"));
        assertThat(spec.path("servers")).isEqualTo(json.readTree("[{\"url\":\"/\"}]"));
        Path target = Path.of("openapi", "openapi.json");
        Files.createDirectories(target.getParent());
        Files.write(target, first);
        assertThat(Files.readAllBytes(target)).isEqualTo(second);
    }

    private byte[] generateSpec() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        // Map으로 읽어 모든 중첩 객체의 키를 정렬하고 배열 순서는 유지한다.
        Object document = json.readValue(body, Object.class);
        var printer = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        return (json.writer().with(printer).writeValueAsString(document) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
