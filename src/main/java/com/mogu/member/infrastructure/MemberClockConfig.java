package com.mogu.member.infrastructure;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MemberClockConfig {
    @Bean("memberClock")
    @ConditionalOnMissingBean(name = "memberClock")
    Clock memberClock() {
        return Clock.systemUTC();
    }
}
