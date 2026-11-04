package com.jack.bytejudge.user.auth.infrastructure.persistence.repository;

import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.infrastructure.persistence.mapper.AuthAuditMapper;

public final class MybatisAuthAudit implements AuthAudit {
    private final AuthAuditMapper mapper;

    public MybatisAuthAudit(AuthAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(AuthAuditEntry entry) {
        mapper.insert(entry, entry.event().name(), entry.outcome().name());
    }
}
