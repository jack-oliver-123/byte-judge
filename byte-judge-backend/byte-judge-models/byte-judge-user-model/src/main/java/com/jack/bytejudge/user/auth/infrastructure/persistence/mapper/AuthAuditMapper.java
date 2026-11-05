package com.jack.bytejudge.user.auth.infrastructure.persistence.mapper;

import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthAuditMapper {
    @Insert("""
            INSERT INTO auth_audit (id, userId, account, event, outcome, reason, requestId)
            VALUES (#{id}, #{entry.userId}, #{entry.account}, #{event}, #{outcome}, #{entry.reason}, #{entry.requestId})
            """)
    void insert(@Param("id") long id, @Param("entry") AuthAuditEntry entry,
                @Param("event") String event, @Param("outcome") String outcome);
}
