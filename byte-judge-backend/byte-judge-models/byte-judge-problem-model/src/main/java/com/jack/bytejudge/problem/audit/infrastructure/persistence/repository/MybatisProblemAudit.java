package com.jack.bytejudge.problem.audit.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.problem.audit.application.port.ProblemAudit;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditEntry;
import com.jack.bytejudge.problem.audit.infrastructure.persistence.entity.ProblemAuditEntity;
import com.jack.bytejudge.problem.audit.infrastructure.persistence.mapper.ProblemAuditMapper;

public final class MybatisProblemAudit implements ProblemAudit {
    private final ProblemAuditMapper mapper;
    private final IdentifierGenerator identifiers;

    public MybatisProblemAudit(ProblemAuditMapper mapper, IdentifierGenerator identifiers) {
        this.mapper = mapper;
        this.identifiers = identifiers;
    }

    @Override
    public void record(ProblemAuditEntry entry) {
        mapper.insert(new ProblemAuditEntity(identifiers.nextId(entry).longValue(),
                entry.operatorId(), entry.operatorAccount(), entry.event().name(),
                entry.outcome().name(), entry.reason(), entry.problemId(), entry.requestId()));
    }
}
