# 后端协作约定

工程结构与分包以 ../docs/05.项目结构.md 为准；技术方向与原型门槛见 ../docs/03.技术选型调研.md 和 ../docs/04.系统架构设计.md。本地命令、端口、配置选择和验证限制见 README.md。

- 当前只包含启动入口和配置。业务包随实际功能创建；common-core 当前只保留 POM。
- 六服务扫描限制在各自 com.jack.bytejudge 根包。服务之间通过 HTTP / 消息协作，各服务持有自己的库账号。
- 新功能按 interfaces → application → domain 组织，infrastructure 实现所需 Port。DTO、VO、数据库实体和 Mapper 留在所属服务。
- 账户状态与角色分离；密码使用随机盐自适应摘要，权限和业务规则遵循需求规格。业务设计不沿用旧模板的固定盐 MD5、ban 角色或代码生成器。
- 本地凭据和生成材料写入 .env.local / .local 并保持忽略；共享配置不得包含真实凭据。
- 修改工程后用 Java 21 执行 mvnw.cmd clean package；改注册、端口或路由时执行 scripts/smoke-test.ps1，并在 README 记录实际结果与尚未验证项。
