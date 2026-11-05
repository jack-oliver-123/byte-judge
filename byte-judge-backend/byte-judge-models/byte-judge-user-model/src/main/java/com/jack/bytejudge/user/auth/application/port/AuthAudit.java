package com.jack.bytejudge.user.auth.application.port;

import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;

public interface AuthAudit {
    void record(AuthAuditEntry entry);
}
