package com.jack.bytejudge.user.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.infrastructure.persistence.mapper.AuthAuditMapper;

public final class MybatisAuthAudit implements AuthAudit {
    private final AuthAuditMapper mapper;
    private final IdentifierGenerator identifiers;

    public MybatisAuthAudit(AuthAuditMapper mapper, IdentifierGenerator identifiers) {
        this.mapper = mapper;
        this.identifiers = identifiers;
    }

    @Override
    public void record(AuthAuditEntry entry) {
        mapper.insert(identifiers.nextId(entry).longValue(), entry, entry.event().name(), entry.outcome().name());
    }
}
