package com.jack.bytejudge.user.auth.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RevokedSessionMapper {
    @Select("SELECT COUNT(*) FROM revoked_session WHERE sessionIdHash = #{hash}")
    int countBySessionIdHash(@Param("hash") byte[] hash);

    /**
     * The unique key makes concurrent or repeated revocations a no-op instead of an error. The
     * statement has no other side effect, so an unrelated SQL failure is still surfaced.
     */
    @Insert("""
            INSERT INTO revoked_session (id, sessionIdHash)
            VALUES (#{id}, #{hash})
            ON DUPLICATE KEY UPDATE sessionIdHash = sessionIdHash
            """)
    void insert(@Param("id") long id, @Param("hash") byte[] hash);
}
