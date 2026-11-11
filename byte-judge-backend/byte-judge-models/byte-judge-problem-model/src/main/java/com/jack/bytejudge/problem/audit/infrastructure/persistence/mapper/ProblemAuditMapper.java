package com.jack.bytejudge.problem.audit.infrastructure.persistence.mapper;

import com.jack.bytejudge.problem.audit.infrastructure.persistence.entity.ProblemAuditEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProblemAuditMapper {
    @Insert("""
            INSERT INTO problem_audit(id, operatorId, operatorAccount, event, outcome, reason, problemId, requestId)
            VALUES(#{entry.id}, #{entry.operatorId}, #{entry.operatorAccount}, #{entry.event},
                   #{entry.outcome}, #{entry.reason}, #{entry.problemId}, #{entry.requestId})
            """)
    int insert(@Param("entry") ProblemAuditEntity entry);
}
