package com.jack.bytejudge.user.auth.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAccountMapper {
    @Select("""
            SELECT id, account, password_hash, role, status
            FROM user_account
            WHERE account = #{account}
            """)
    StoredAccountRow findAccount(@Param("account") String account);

    record StoredAccountRow(long id, String account, String passwordHash, String role, String status) { }
}
