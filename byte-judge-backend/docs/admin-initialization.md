# 管理员受控初始化

本文对应任务 #6，仅提供管理员创建命令。登录、共享 Session、权限、限频与 Nacos Config 由后续任务实现；创建成功不等于认证业务已经验收。

## 前提

- Java 21；首次构建和测试需要可用的 Maven 下载源。
- 用户服务使用所属 MySQL 数据库及独立账号；账号需要执行 Flyway 迁移所需的 DDL 和账户插入权限。
- 本地基础设施沿用现有 Compose，真实凭据只从 `.env.local` 或受控环境注入。管理员密码与数据库连接密码是不同的凭据。

## 本地执行（PowerShell）

在后端根目录中执行。下面只改变当前进程的 Java 环境，以避开旧 `use-java21.ps1` 在 Windows PowerShell 下读取标准错误输出的问题：

```powershell
$env:JAVA_HOME = 'C:\Develop\JDK\jdk21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java --version
. ./scripts/local-env.ps1
./mvnw.cmd -pl :byte-judge-user-model -am package -DskipTests
java -jar ./byte-judge-models/byte-judge-user-model/target/byte-judge-user-model-0.0.1-SNAPSHOT.jar --create-admin
$LASTEXITCODE
```

如基础设施尚未启动，先按后端 README 执行启动步骤。命令会依次提示账户、密码和确认密码；在真实终端中密码输入不回显。不要把密码放进命令行参数、脚本源码、可提交配置或终端历史。

命令从正常服务入口分流到专用非 Web 上下文，只装配数据库、Flyway、MyBatis 和密码摘要所需组件；不启动 HTTP 或管理端口，也不注册 Nacos、不依赖 Redis/RabbitMQ。无 `--create-admin` 参数时仍按原方式启动服务，不自动创建管理员。

非 local 环境使用受控环境设置 `SPRING_PROFILES_ACTIVE`、`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 等连接信息。初始化必须允许执行正式迁移；不得通过清库或打开自动 baseline 绕过不一致的已有数据库。复用已有非空库而没有 Flyway 历史时，应先受控核对和纳管现有结构。

## 输入规则

- 账户：4～32 位 ASCII 字母、数字或下划线。去首尾空白后验证，转小写保存，全局唯一。
- 密码：8～64 个 Unicode 码点。空白保留、不截断；补充平面字符按一个码点计算。
- 确认密码：必须与原密码完全一致。
- 支持创建多个管理员。重复账户（包括大小写、首尾空白归一化冲突）只报错；同名普通用户不会被升级，已有密码、角色和状态不会被覆盖。

自动化环境没有终端时，标准输入支持两种 UTF-8 格式：账户、密码、确认密码各一行；或一个 JSON 对象，其键必须恰好为 `account`、`password`、`confirmation`，值均为字符串。JSON 模式支持转义的 LF、CR、NUL 和其他 Unicode 字符，适用于不能由交互终端或单行协议完整表达的密码，不额外收窄密码字符规则。重复键、额外字段、多个 JSON 文档、无效类型及解析失败均拒绝，错误不包含解析器的原始输入诊断。

由受控父进程通过匿名管道写入并关闭输入；不要使用含明文密码的示例命令、临时明文文件或 Windows PowerShell 5 默认文本管道传递 Unicode 密码。三行模式不接受额外内容；两种模式均拒绝无效 UTF-8，避免悄悄截断或替换密码字符。

## 输出与退出码

| 退出码 | 输出标识 | 含义 |
| --- | --- | --- |
| 0 | ADMIN_CREATED | 管理员已创建，附规范化账户 |
| 2 | ADMIN_INPUT_INVALID | 输入格式、账户、密码或确认密码无效 |
| 3 | ADMIN_ACCOUNT_CONFLICT | 账户已存在，已有账户未改变 |
| 1 | ADMIN_INITIALIZATION_FAILED | 数据库、迁移或其他运行失败；检查受控配置与结构 |

错误只输出固定的安全提示，不输出底层 SQL、堆栈、连接秘密或密码摘要。数据库异常的诊断应由有权限的维护者在受控环境检查连接与迁移状态。

## 密码存储与迁移

数据库只保存带随机盐的 Argon2id 摘要。编码包括 `{argon2}` 算法标识，以及标准 Argon2id 格式中的版本、参数、随机盐与摘要。使用 Spring Security PasswordEncoder 验证，不能解密回原密码。

本次选定参数为随机盐 16 字节、摘要 32 字节、内存 65,536 KiB、迭代 3 次、并行度 1。在 Windows 11、Intel Core i7-12700H、Java 21.0.11 开发机上，最终完整构建中，预热后 5 次实际摘要校验耗时为 190.12、217.13、202.80、221.30、200.47 ms（此前单文件运行样本为 138～172 ms，反映负载差异）；该参数同时增加内存和计算成本，并适合当前 256 MiB 的单进程运行配置。此为开发环境校准，不代表生产吞吐或延迟保证；部署硬件变化后需复测，避免为追求单次耗时使并发登录耗尽内存。

用户服务首个 Flyway 迁移建立 `user_account`，包含唯一的规范化账户、摘要、独立角色与状态、`auth_epoch` 和时间字段。新管理员为 `admin`、`normal`、`auth_epoch=0`。数据库唯一约束处理并发同名创建；不使用覆盖式插入。local 用户服务现已启用迁移，其他服务的迁移配置不受本任务影响。

## 验证方式

主要行为入口是实际 Java 子进程命令的输出及退出码，必要时在持久化边界检查摘要、唯一性和账户事实。测试使用 Testcontainers 创建独立的 MySQL 8.4.13 容器和随机测试凭据，不读取 `.env.local`，不访问或清理现有本地库；Docker 必须可用，缺少环境时测试失败，不静默跳过。

```powershell
./mvnw.cmd -pl :byte-judge-user-model -am test -Dtest=AdminInitializationTest -Dsurefire.failIfNoSpecifiedTests=false
```

正式交付前在 Java 21 下执行完整 `clean package`。测试、构建和运行证据记录在后端 README；本任务的命令测试不代表后续登录接口或全项目业务验收通过。
