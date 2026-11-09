package com.jack.bytejudge.problem.audit.application.port;

import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditEntry;

/** Service-wide audit sink shared by every problem feature. */
public interface ProblemAudit {
    void record(ProblemAuditEntry entry);
}
