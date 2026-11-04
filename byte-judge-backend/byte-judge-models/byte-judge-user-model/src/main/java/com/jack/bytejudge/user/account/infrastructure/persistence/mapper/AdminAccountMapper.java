package com.jack.bytejudge.user.account.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAccountMapper {
    @Insert("""
            INSERT INTO user_account (account, password_hash, role, status, auth_epoch)
            VALUES (#{account}, #{passwordHash}, 'admin', 'normal', 0)
            """)
    void insertAdministrator(@Param("account") String account, @Param("passwordHash") String passwordHash);
}
