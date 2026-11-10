package com.mogu.auth.infrastructure;

import com.mogu.common.auth.CurrentMember;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AuthWebConfig implements WebMvcConfigurer {
    @Bean
    CurrentMember currentMember() {
        return new SecurityContextCurrentMember();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginMemberArgumentResolver(currentMember()));
        resolvers.add(new PendingRegistrationArgumentResolver());
    }
}
