package com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper;

import com.jack.bytejudge.problem.admin.domain.model.ProblemAuditEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ProblemAuditMapper {
    @Insert("""
            INSERT INTO problem_audit(id, operatorId, operatorAccount, event, outcome, reason, problemId, requestId)
            VALUES(#{id}, #{entry.operatorId}, #{entry.operatorAccount}, #{event}, #{outcome},
                   #{entry.reason}, #{entry.problemId}, #{entry.requestId})
            """)
    int insert(@Param("id") long id, @Param("entry") ProblemAuditEntry entry,
               @Param("event") String event, @Param("outcome") String outcome);
}
