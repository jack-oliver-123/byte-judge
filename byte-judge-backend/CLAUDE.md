# 后端协作约定

工程结构与分包以 ../docs/05.项目结构.md 为准；技术方向与原型门槛见 ../docs/03.技术选型调研.md 和 ../docs/04.系统架构设计.md。本地命令、端口、配置选择和验证限制见 README.md。

- 涉及管理员创建时，读取 docs/admin-initialization.md，按命令入口及持久化边界验证；登录与共享会话的进度以 README 为准。
- 六服务扫描限制在各自 com.jack.bytejudge 根包，通过 HTTP / 消息协作；四个数据 owner 各持独立库账号，网关和判题服务无业务库账号。
- `byte-judge-*-model` 是独立可执行服务，不是共享模型库；服务之间不得依赖彼此的可执行模块。
- PowerShell 构建前点加载 `. ./scripts/use-java21.ps1`，只切换当前进程；不修改系统默认 Java。
- IDE 启动前注入 `.env.local` 变量，或点加载 `scripts/local-env.ps1`；Spring 不自动读取该文件。
- 网关路由验证须同时取得下游 404 和唯一探针的目标服务访问日志；单独返回 404 不算通过。
- 数据库变更通过所属服务 Flyway 迁移并验证；构建与打包成功不替代业务行为测试，已有数据不得以清库方式迁移。
- 新功能按 interfaces → application → domain 组织，infrastructure 实现所需 Port。DTO、VO、数据库实体和 Mapper 留在所属服务。
- 账户状态与角色分离；密码使用随机盐自适应摘要，权限和业务规则遵循需求规格。业务设计不沿用旧模板的固定盐 MD5、ban 角色或代码生成器。
- 本地凭据和生成材料写入 .env.local / .local 并保持忽略；共享配置不得包含真实凭据。
- 修改工程后用 Java 21 执行 mvnw.cmd clean package；改注册、端口或路由时执行 scripts/smoke-test.ps1，并在 README 记录实际结果与尚未验证项。
