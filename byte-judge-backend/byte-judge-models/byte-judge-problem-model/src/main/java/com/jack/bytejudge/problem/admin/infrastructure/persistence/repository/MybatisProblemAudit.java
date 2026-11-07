package com.jack.bytejudge.problem.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.problem.admin.application.port.ProblemAudit;
import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditEntry;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper.ProblemAuditMapper;

public final class MybatisProblemAudit implements ProblemAudit {
    private final ProblemAuditMapper mapper;
    private final IdentifierGenerator identifiers;

    public MybatisProblemAudit(ProblemAuditMapper mapper, IdentifierGenerator identifiers) {
        this.mapper = mapper;
        this.identifiers = identifiers;
    }

    @Override
    public void record(ProblemAuditEntry entry) {
        mapper.insert(identifiers.nextId(entry).longValue(), entry, entry.event().name(), entry.outcome().name());
    }
}
