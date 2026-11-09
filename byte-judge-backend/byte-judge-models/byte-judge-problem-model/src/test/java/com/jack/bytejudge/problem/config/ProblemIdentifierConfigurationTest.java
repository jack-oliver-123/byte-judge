package com.jack.bytejudge.problem.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the mandatory Snowflake node configuration. The runner loads only the production
 * identifier configuration, so neither Redis nor MySQL nor Nacos is touched, and it removes the
 * ambient system environment so an operator's exported variables cannot mask a missing value.
 */
class ProblemIdentifierConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProblemIdentifierConfiguration.class)
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            });

    @Test
    void missingWorkerIdFailsContextStartup() {
        runner.withPropertyValues("byte-judge.problem.datacenter-id=1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("byte-judge.problem.worker-id");
                });
    }

    @Test
    void missingDatacenterIdFailsContextStartup() {
        runner.withPropertyValues("byte-judge.problem.worker-id=1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasStackTraceContaining("byte-judge.problem.datacenter-id");
                });
    }

    @Test
    void bothNodeValuesCreateTheIdentifierGenerator() {
        runner.withPropertyValues("byte-judge.problem.worker-id=7",
                        "byte-judge.problem.datacenter-id=3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdentifierGenerator.class);
                    assertThat(context.getBean(IdentifierGenerator.class).nextId(new Object()))
                            .isNotNull();
                });
    }

    /** A default in application.yml would let a misconfigured deployment start with colliding ids. */
    @Test
    void productionYamlLeavesBothSnowflakeValuesUnresolved() throws IOException {
        var source = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .stream()
                .findFirst()
                .orElseThrow();

        assertThat(source.getProperty("byte-judge.problem.worker-id"))
                .isEqualTo("${BYTE_JUDGE_PROBLEM_WORKER_ID}");
        assertThat(source.getProperty("byte-judge.problem.datacenter-id"))
                .isEqualTo("${BYTE_JUDGE_PROBLEM_DATACENTER_ID}");
    }
}
