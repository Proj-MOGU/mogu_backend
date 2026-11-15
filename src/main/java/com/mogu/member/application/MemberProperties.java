package com.mogu.member.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.member")
public record MemberProperties(Duration nicknameHoldPeriod) {
    public MemberProperties {
        nicknameHoldPeriod = nicknameHoldPeriod == null ? Duration.ofDays(30) : nicknameHoldPeriod;
        if (nicknameHoldPeriod.isNegative() || nicknameHoldPeriod.isZero())
            throw new IllegalArgumentException("mogu.member.nickname-hold-period");
    }
}
