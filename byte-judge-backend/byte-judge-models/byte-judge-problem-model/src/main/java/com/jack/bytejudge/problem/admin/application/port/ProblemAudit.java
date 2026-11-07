package com.jack.bytejudge.problem.admin.application.port;

import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditEntry;

public interface ProblemAudit {
    void record(ProblemAuditEntry entry);
}
