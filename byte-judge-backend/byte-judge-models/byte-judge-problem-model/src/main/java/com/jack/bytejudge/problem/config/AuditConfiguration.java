package com.jack.bytejudge.problem.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.problem.audit.application.port.ProblemAudit;
import com.jack.bytejudge.problem.audit.infrastructure.persistence.mapper.ProblemAuditMapper;
import com.jack.bytejudge.problem.audit.infrastructure.persistence.repository.MybatisProblemAudit;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the service-wide audit sink and scans only its own mapper package. */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = ProblemAuditMapper.class)
public class AuditConfiguration {

    @Bean
    ProblemAudit problemAudit(ProblemAuditMapper mapper, IdentifierGenerator identifiers) {
        return new MybatisProblemAudit(mapper, identifiers);
    }
}
