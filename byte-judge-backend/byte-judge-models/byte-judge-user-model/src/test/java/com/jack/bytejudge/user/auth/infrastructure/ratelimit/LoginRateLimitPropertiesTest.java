package com.jack.bytejudge.user.auth.infrastructure.ratelimit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the production defaults independently from the short values used by time-controlled tests. */
class LoginRateLimitPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableProperties.class);

    @Test
    void theDefaultPolicyIsTenFailuresInFifteenMinutesAndAFifteenMinuteBlock() {
        runner.run(context -> {
            LoginRateLimitProperties properties = context.getBean(LoginRateLimitProperties.class);
            assertThat(properties.window()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.maxFailures()).isEqualTo(10);
            assertThat(properties.blockDuration()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.namespace()).isEqualTo("byte-judge:auth:login");
        });
    }

    @Test
    void testsMaySupplyIndependentTimeAndThresholdValues() {
        runner.withPropertyValues(
                        "byte-judge.auth.rate-limit.window=6s",
                        "byte-judge.auth.rate-limit.max-failures=3",
                        "byte-judge.auth.rate-limit.block-duration=2s",
                        "byte-judge.auth.rate-limit.namespace=test:login")
                .run(context -> {
                    LoginRateLimitProperties properties = context.getBean(LoginRateLimitProperties.class);
                    assertThat(properties.window()).isEqualTo(Duration.ofSeconds(6));
                    assertThat(properties.maxFailures()).isEqualTo(3);
                    assertThat(properties.blockDuration()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.namespace()).isEqualTo("test:login");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LoginRateLimitProperties.class)
    static class EnableProperties { }
}
