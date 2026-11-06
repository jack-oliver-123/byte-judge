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
     * A duplicate key is surfaced rather than swallowed so the adapter can distinguish a repeated
     * revocation from a snowflake-id collision; the latter must fail closed.
     */
    @Insert("""
            INSERT INTO revoked_session (id, sessionIdHash)
            VALUES (#{id}, #{hash})
            """)
    void insert(@Param("id") long id, @Param("hash") byte[] hash);
}
