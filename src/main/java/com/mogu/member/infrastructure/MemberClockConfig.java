package com.mogu.member.infrastructure;

import com.mogu.member.application.MemberProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemberProperties.class)
public class MemberClockConfig {
    @Bean("memberClock")
    @ConditionalOnMissingBean(name = "memberClock")
    Clock memberClock() {
        return Clock.systemUTC();
    }
}
