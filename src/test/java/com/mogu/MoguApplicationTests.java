package com.mogu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MoguApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("애플리케이션 컨텍스트가 정상적으로 로드된다")
    void contextLoads() {
        // when
        MoguApplication application = context.getBean(MoguApplication.class);

        // then
        assertThat(application).isNotNull();
    }

    @Test
    @DisplayName("헬스 체크는 인증 없이 접근할 수 있다")
    void healthIsPublic() throws Exception {
        // when
        var result = mockMvc.perform(get("/actuator/health"));

        // then
        result.andExpect(status().isOk());
    }

    @Test
    @DisplayName("임의 경로는 인증 없이 요청하면 401을 반환한다")
    void arbitraryPathRequiresAuthentication() throws Exception {
        // when
        var result = mockMvc.perform(get("/api/anything"));

        // then
        result.andExpect(status().isUnauthorized());
    }
}
