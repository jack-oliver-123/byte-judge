# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

仓库级说明（技术栈、命令、版本坑、已删除的帖子/ES 模块）见上一级 `../CLAUDE.md`，此处只记后端内部约定。

## 数据层约定

`application.yml` 里 `mybatis-plus.configuration.map-underscore-to-camel-case: false`，与 MyBatis 默认值相反。实体字段名必须与数据库列名逐字相同，而本项目的列名是驼峰（`userAccount`、`isDelete`），不是下划线。写错不会报错，只会查不到数据或插入失败。

- **主键**：实体用 `@TableId(type = IdType.ASSIGN_ID)`，插入前由 MyBatis-Plus 生成雪花 ID。但 `sql/create_table.sql` 里列定义是 `bigint auto_increment`——两者不一致，实际生效的是雪花 ID，自增不会被用到。新增表时沿用雪花，不要改回自增。
- **Long 精度**：`config/JsonConfig` 把所有 `Long` 序列化成字符串（前端 JS 超过 `2^53-1` 会丢精度）。所以接口对外的 id 都是字符串，请求体里的数字字符串反序列化时会自动转回 `Long`。
- **逻辑删除**：全局字段 `isDelete`（`1` 已删 / `0` 未删），实体上加 `@TableLogic`。后果是 `removeById`、`remove(wrapper)` 实际执行的是 `UPDATE isDelete = 1`，所有普通查询自动附加 `isDelete = 0`。要物理删除必须绕过 `@TableLogic`，不要以为 `removeById` 是真删。

## 用户与鉴权

- 角色取值 `user` / `admin` / `ban`，定义在 `model/enums/UserRoleEnum`，与前端 `src/access/accessEnum.ts` 的 `ACCESS_ENUM` 逐字对齐。改一边必须同步另一边，否则前端权限判断会失效。
- 密码是 `md5(SALT + 明文)`，`SALT` 硬编码在 `service/impl/UserServiceImpl`。改这个常量会使所有已注册用户无法登录。
- 登录态存在 Session，key 为 `UserConstant.USER_LOGIN_STATE`（值 `"user_login"`），不是 JWT。`getLoginUser` 在未登录时抛 `BusinessException(NOT_LOGIN_ERROR)`，不返回 null；允许未登录的场景用 `getLoginUserPermitNull`。

## 新增业务模块

用 `generate/CodeGenerator`：改 `main` 里的 `dataName` / `dataKey` / `upperDataKey` 后直接运行。它按 `src/main/resources/templates/` 下的 FreeMarker 模板生成 Controller、Service、DTO、VO，输出到项目根目录的 `generator/`（已在 `.gitignore` 习惯范围外，需手动移入 `com.jack.oj` 对应包），再按文件里的 `// todo` 补业务逻辑。

生成代码沿用本文件上面的数据层约定。它不生成 Entity 和 Mapper，这两件用 MyBatisX 插件从建表语句生成。
