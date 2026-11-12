package com.mogu.auth.infrastructure;

import com.mogu.auth.application.RateLimitRule;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.auth.rate-limit")
public record RateLimitProperties(Duration queryTimeout, Map<String, Rule> rules) {
    public RateLimitProperties {
        if (queryTimeout == null || queryTimeout.toMillis() < 1)
            throw new IllegalArgumentException("rate-limit.query-timeout");
        rules = Map.copyOf(rules);
        for (var bucket : new RateLimitRule[] {RateLimitRule.LOGIN_FAILURES, RateLimitRule.REFRESH_FAILURES}) {
            if (rules.get(bucket.ruleName()) == null
                    || rules.get(bucket.ruleName()).refillInterval() == null)
                throw new IllegalArgumentException("Missing refill interval: " + bucket);
        }
        for (var rule : RateLimitRule.values()) {
            if (!rules.containsKey(rule.ruleName())) throw new IllegalArgumentException("Missing rule: " + rule);
        }
    }

    public Rule rule(RateLimitRule rule) {
        return rules.get(rule.ruleName());
    }

    public record Rule(int limit, Duration window, Duration refillInterval) {
        public Rule {
            if (limit < 1
                    || window == null
                    || window.toMillis() < 1
                    || (refillInterval != null && refillInterval.toMillis() < 1))
                throw new IllegalArgumentException("Invalid rate-limit rule");
        }
    }
}
