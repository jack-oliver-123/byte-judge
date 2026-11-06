package com.jack.bytejudge.user.auth.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAccountMapper {
    @Select("""
            SELECT id, account, password_hash, role, status, auth_epoch
            FROM user_account
            WHERE account = #{account}
            """)
    StoredAccountRow findAccount(@Param("account") String account);

    @Select("""
            SELECT id, account, role, status, auth_epoch
            FROM user_account
            WHERE id = #{userId}
            """)
    AccountSnapshotRow findAccountSnapshot(@Param("userId") long userId);

    record StoredAccountRow(long id, String account, String passwordHash, String role,
                            String status, long authEpoch) { }

    record AccountSnapshotRow(long id, String account, String role, String status, long authEpoch) { }
}
