package com.jack.bytejudge.problem.config;

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake identifiers must be unique across every deployed problem-service instance. Both node
 * values are deliberately mandatory: a missing deployment value fails startup instead of silently
 * falling back to a colliding default.
 */
@Configuration(proxyBeanMethods = false)
public class ProblemIdentifierConfiguration {

    @Bean
    IdentifierGenerator problemIdentifiers(
            @Value("${byte-judge.problem.worker-id}") long workerId,
            @Value("${byte-judge.problem.datacenter-id}") long datacenterId) {
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }
}
