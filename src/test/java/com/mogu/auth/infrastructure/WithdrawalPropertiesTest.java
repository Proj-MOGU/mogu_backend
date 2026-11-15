package com.mogu.auth.infrastructure;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WithdrawalPropertiesTest {
    @Test
    @DisplayName("기본 제한은 30일이며 양수 설정만 기동된다")
    void validatesRestriction() {
        // given
        var runner = new ApplicationContextRunner().withUserConfiguration(WithdrawalConfig.class);
        // when / then
        runner.run(c -> assertThat(c.getBean(WithdrawalProperties.class).rejoinRestriction())
                .isEqualTo(Duration.ofDays(30)));
        runner.withPropertyValues("mogu.auth.withdrawal.rejoin-restriction=P7D")
                .run(c -> assertThat(c.getBean(WithdrawalProperties.class).rejoinRestriction())
                        .isEqualTo(Duration.ofDays(7)));
        for (String value : new String[] {"PT0S", "-PT1S"})
            runner.withPropertyValues("mogu.auth.withdrawal.rejoin-restriction=" + value)
                    .run(c -> assertThat(c).hasFailed());
    }
}
