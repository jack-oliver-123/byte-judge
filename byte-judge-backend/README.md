# Byte Judge 后端

Java 21 / Maven 3.9.15；Spring Boot 4.0.9、Cloud 2025.1.4、Alibaba 2025.1.0.0。根工程聚合 commons、gateway、models，共 11 个 POM；common-core 与 common-web 为普通 JAR，网关和五个业务服务为可执行 JAR。

## 本地启动（PowerShell）

要求 Docker Desktop 正常运行，空闲端口见下表。命令在后端根目录执行：

```powershell
. ./scripts/use-java21.ps1
./mvnw.cmd clean package
./scripts/start-infra.ps1
./scripts/start-services.ps1
./scripts/smoke-test.ps1
```

JDK 默认 C:\Develop\JDK\jdk21；可向 use-java21.ps1、start-services.ps1 传 -JdkPath。只改变当前进程环境，不改变系统 Java 17。init-local.ps1 自动生成并保留 .env.local 口令；其生成 SQL 位于被忽略的 .local/mysql-init，仅在空 MySQL 卷首次初始化执行。不要删除已有环境文件后复用旧卷，环境变量变化不会更改卷内账号口令。

start-services.ps1 将变量注入六个子进程，使用 -Xmx256m；应用日志统一在后端根目录 log/，PID 保存在 .local/pids。IDE 启动需先在启动环境注入 .env.local 的变量（或在当前终端点加载 scripts/local-env.ps1）；Spring 不自动加载此文件。可用 SERVER_PORT、MANAGEMENT_PORT、SERVICE_IP 覆盖单实例配置。管理端口需独立配置，避免第二实例冲突。网关调用用户服务内部身份核验使用 `BYTE_JUDGE_INTERNAL_GATEWAY_CREDENTIAL`（至少 32 字符、无默认值）；init-local.ps1 会在 .env.local 中为缺失键生成随机值，非 local 环境必须由部署方注入。用户服务审计使用分配式雪花主键，`BYTE_JUDGE_AUDIT_WORKER_ID` / `BYTE_JUDGE_AUDIT_DATACENTER_ID` 无默认值必须提供；同一时刻运行的每个用户服务实例必须使用全局唯一的组合，冲突会生成重复主键。已有 .env.local 不会自动补齐新键，需重新运行 init-local.ps1。

每个服务写入 log/<服务简称>/：application.log 为滚动应用日志（单文件 10MB，历史 7 天、归档总量最多 100MB），console.out.log / console.err.log 为脚本启动的控制台输出，access/ 为 Tomcat 访问日志，nacos/ 为 Nacos 客户端日志。构建和验收输出可重定向到 log/build/。log/ 整体被 Git 忽略，Tomcat 工作目录仍放 .local/runtime，历史日志保留在原目录。

脚本注入绝对 BYTE_JUDGE_LOG_DIR，确保六进程路径一致。IDE 的工作目录设为后端根目录，并配置 VM 参数 -DJM.LOG.PATH=<后端绝对路径>/log/<服务简称>，使 [Nacos 客户端日志](https://nacos.io/en-us/docs/system-configurations.html) 同样落入 log/；也可显式设置 BYTE_JUDGE_LOG_DIR。Docker 基础设施日志继续由容器日志驱动与命名卷管理，可用 docker compose logs 查看。

| 服务 | 应用端口 | 本机健康检查 |
| --- | --- | --- |
| gateway | 8080 | 9080/actuator/health |
| user-model | 8081 | 9081/actuator/health |
| problem-model | 8082 | 9082/actuator/health |
| submission-model | 8083 | 9083/actuator/health |
| contest-model | 8084 | 9084/actuator/health |
| judge-model | 8085 | 9085/actuator/health |

本地所有 Java 和管理端口绑定 127.0.0.1。管理端口只暴露 health，无需鉴权且不显示细节；此为隔离本机开发约定。当前只交付认证与最小管理员权限，其他业务鉴权、生产部署和前端尚未实现，不能直接用于公开部署。judge 当前没有消费者或执行器，保持不接任务。

基础设施：MySQL 3306、Redis 16379（避开本机已有 6379，可通过 REDIS_PORT 覆盖）、RabbitMQ 5672 / 管理 15672；Nacos API 8848、gRPC 9848、控制台 [本地控制台](http://127.0.0.1:8088/)。全部仅绑定回环。四个数据库 bj_user/problem/submission/contest 对应同名账号，口令分别独立；网关与 judge 无业务库账号。Nacos 使用 standalone + Derby，本地关闭鉴权。网关和用户服务通过 `spring.config.import` 必需导入 `BYTE_JUDGE` 组的 `byte-judge-common.yml` 及各自服务配置；启动脚本先发布 `infra/nacos/config/` 的非敏感模板并读回核对。配置中心不可达或缺少必要入口配置时启动失败；环境可通过 `NACOS_CONFIG_GROUP`、`NACOS_CONFIG_NAMESPACE` 隔离，真实凭据仍由环境注入。其余四个服务当前只接入注册发现。

## 验证口径

smoke-test.ps1 失败抛错并返回非零。验证六服务 UP 和 Nacos 健康注册、四数据库账号可连接与跨库拒绝、Redis 认证 PING、RabbitMQ 认证管理 API，以及四条网关路由的下游 404 和唯一探针的目标服务访问记录。单独 404 不算通过。脚本最长等待服务 180 秒；网络调用有独立超时。

网关按 docs/04 §2.3 显式配置四组 lb:// 路由，保留原始路径；没有服务名通配路由，judge、/internal/v1/** 和下游 Actuator 不匹配公共路由，返回 404。访问日志仅记录方法、路径和状态，本地探针用后可清理 log/<服务简称>/access，避免未来敏感参数进入日志。

当前依赖按服务声明，根 POM 只管理版本与构建规则，两个聚合层保持纯 POM；common-core 保持无框架依赖的普通 JAR，common-web 承载 MVC / Security / Session / 日志适配且只被网关与用户服务依赖。Spring Boot 父工程管理编译、测试、打包插件及其生态依赖版本；Cloud、Alibaba、MyBatis-Plus 分别使用 BOM。Enforcer 检查 Java 21、Maven 3.9+，阻止 Boot2/3 MyBatis-Plus starter、WebFlux starter 和可执行服务模块间的代码依赖。

| 模块 | 主要依赖 |
| --- | --- |
| gateway | Gateway MVC、Actuator、Nacos Discovery、LoadBalancer、Caffeine |
| user / problem / submission / contest | MVC、Validation、RestClient、Actuator、Nacos Discovery、LoadBalancer、Caffeine、MyBatis-Plus Boot4、MySQL、Flyway、Redis、AMQP |
| user 额外 | Spring Security Crypto + Bouncy Castle，实现 Argon2id 管理员密码摘要；Testcontainers MySQL 为 test scope |
| judge | MVC、Validation、RestClient、Actuator、Nacos Discovery、LoadBalancer、Caffeine、AMQP；无 MySQL / Redis / Session 依赖 |
| 六个启动模块测试 | Spring Boot Test、MVC Test，均为 test scope |

MyBatis-Plus 固定 3.5.18，在根 POM 通过 mybatis-plus.version 和 BOM 管理，依据 [官方 Boot4 安装说明](https://baomidou.com/en/getting-started/install/)；分页解析插件仅在需要时由子模块显式引用，版本由同一 BOM 管理。MySQL Connector/J 固定 9.7.0，在根 POM 显式设置 mysql.version，由继承的 Spring Boot dependencyManagement 使用该属性统一管理。四个数据 owner 按需声明这两项依赖，网关、judge 和 common-core 不引入。MySQL 驱动、Flyway MySQL 扩展和 Caffeine 使用 runtime scope。

四个数据 owner 的 application-local.yml 配置所属库、独立账号、最大 5 连接池；Redis / RabbitMQ 口令来自环境变量。新增 starter 已参与健康检查。用户服务已有正式账户迁移，local 下启用 Flyway；另外三个数据 owner 尚无业务迁移，仍保持原配置。非 local 环境需提供自己的连接与迁移配置，不能以清库或自动 baseline 绕过已有结构核对。

## 管理员受控初始化

初始化命令、输入规则、退出码、密码参数和测试方式见 [管理员受控初始化](docs/admin-initialization.md)。通过 `java -jar <用户服务制品> --create-admin` 进入独立非 Web 模式，从交互控制台或受控 UTF-8 标准输入读取凭据；正常启动不创建管理员。允许多个管理员，重复账户只报错，不覆盖密码、不提升已有普通用户。命令只装配数据库与密码处理所需组件，不启动网关、Nacos、Redis 或 RabbitMQ。

该项测试使用独立临时 MySQL，不操作现有本地库。登录、当前身份查询与退出由下一节说明的 #7 切片实现；实时身份核验与最小管理员接口见 #8 记录；登录限频留给 #10，本分支尚未实现。

## 登录、当前身份与退出

接口协议、错误码、Cookie/CSRF 生命周期、管理员入口和手工调用步骤见 [登录、当前身份、退出与管理员权限接口](docs/auth-api.md)。网关与用户服务使用一致的 Redis Session 契约（indexed repository，命名空间 `byte-judge:session`，30 分钟闲置）；BJSESSION Cookie 在 local 显式关闭 Secure，其他环境默认 Secure。用户服务负责 CSRF 校验和登录/退出写入；网关仅转发 Cookie、CSRF 头和下游 Set-Cookie，既不读取也不写入会话（关闭 request-handled 事件并使用无操作 FlashMapManager），避免在用户服务轮换或撤销会话后回写残片。网关在转发前剥离客户端传入的 `X-User-*`、`X-Internal-*` 与 `Forwarded` / `X-Forwarded-*` 头，下游服务不会收到可伪造的身份头。`GET /api/v1/admin/users/me` 提供最小管理员身份入口；每个受保护请求都由用户服务按权威角色、账户状态和 `authEpoch` 实时核验，网关入口另经独立服务身份调用内部身份接口，浏览器管理员 Session 不能授权该内部接口。

认证请求日志由 common-web 的基础过滤器生成请求标识（非入口只接受合法格式的下游标识），写入响应头与 MDC，日志仅含方法、状态和耗时，不含查询、请求体、Cookie 或凭据。MVC 与 Security 错误出口都输出统一 `code/data/message/requestId/serverTime` 结构并保留真实 HTTP 状态。

Spring Security Web 与 Spring Session 的适配集中在 `common-web`，由网关和用户服务显式 `@Import`；网关和用户服务的日志及入口请求标识配置已接入 Nacos Config，Sentinel 规则仍待后续任务装配。starter 存在不能代表 Outbox、消息消费或迁移已实现。P-01 完整验证、其他原型与 AT 验收仍按架构文档执行，双 submission / judge 演练随后进行。

## 停止与恢复

```powershell
./scripts/stop-services.ps1
docker compose --env-file .env.local stop
docker compose --env-file .env.local up -d --wait
```

常规停止保留命名卷。删除卷会丢失本地数据，应仅在明确需要重置时操作。Compose 只编排四件基础设施；Java 服务通过 IDE、JAR 或脚本运行。

## 构建与运行证据

2026-11-03：Oracle JDK 21.0.11+9-LTS-211、Maven Wrapper 3.9.15 下执行 clean package，10 个 reactor 项全部 SUCCESS，六个 Boot JAR 与 common-core 普通 JAR 产出。依赖树解析到 Gateway / Cloud Commons 5.0.4、Nacos Client 3.1.1。

同日实际执行 start-infra.ps1，四容器全部 healthy；start-services.ps1 后 smoke-test.ps1 退出码 0：六服务 UP 与健康注册、四库所属账号连接及 12 组跨库拒绝、Redis 认证 PING、RabbitMQ 认证管理 API、四组 lb 路由与下游唯一探针访问记录、公共路径白名单检查全部通过。脚本使用 Nacos v3 客户端实例 API，并有界等待 Tomcat 缓冲访问日志落盘。PowerShell 脚本语法、Compose 配置解析和 git diff --check 通过。业务验收、配置中心、Java 数据访问与迁移、双实例、Session、可靠消息及沙箱仍待验证。

同日完善 POM 后，Java 21 下 clean verify 的 10 个模块全部成功；当前没有测试类，该结果只证明编译、依赖及打包通过。实际解析 MyBatis-Plus 3.5.18、Flyway 11.14.1、Connector/J 9.7.0（后两者由 Boot BOM 管理）。Java 17 执行 validate 被 Enforcer 按预期拒绝。重新启动后 smoke-test.ps1 退出码 0，数据库、Redis、RabbitMQ 已加入的健康检查及原有注册、账号隔离、网关转发检查全部通过。产物检查确认 gateway / judge 不含数据库与 Redis 依赖，common-core 为普通 JAR。迁移仍关闭，没有声明 Flyway 执行验证通过。

2026-11-04：根 POM 显式管理 mysql.version=9.7.0，保留 MyBatis-Plus BOM 3.5.18。Java 21.0.11 下执行 mvnw.cmd -B clean package，10 个模块全部 SUCCESS；产物检查确认四个数据 owner 含 Connector/J 9.7.0 和 MyBatis-Plus Boot4 starter 3.5.18，gateway / judge 不含 MySQL / MyBatis 依赖，common-core 仍为普通 JAR。Windows PowerShell 点加载 use-java21.ps1 时因 java -version 的标准错误输出触发 NativeCommandError，本次在当前进程显式设置同一 JDK 的 JAVA_HOME / PATH，并用 java --version 确认后完成构建；该脚本兼容问题已在任务 #7 中修复（改用 `java --version` 校验并兼容 Windows PowerShell）。当前没有测试类；本次未执行服务启动、数据库 CRUD 或迁移验证。

2026-11-04，任务 #6：新增管理员受控初始化、Argon2id 和正式账户迁移。Java 21.0.11 下执行 `mvnw clean package -B`，10 个 reactor 项全部 SUCCESS，34 项命令/持久化行为测试全部通过（0 failure、0 error、0 skipped），总耗时 3 分 55 秒。测试使用 Testcontainers 2.0.5 和独立 MySQL 8.4.13，覆盖输入边界、Unicode/空白、JSON 换行密码、随机盐、已有普通用户与管理员不覆盖、双进程并发、数据库失败、迁移关闭、普通 Web 启动不建账户及 TRACE 下摘要不泄露。完整输出位于 `log/build/admin-initialization-final-package.log`。

同次对实际可执行 JAR 以 `-Xmx256m` 在另一个临时 MySQL 上验证：创建退出码 0、重复退出码 3、原账户与摘要保持不变、JSON 换行密码创建成功、Flyway 和 Argon2id 存储检查通过。临时容器等待 MySQL 健康后执行并在结束后移除，未向现有本地库写入管理员。制品包含 Security Crypto 7.0.8、Bouncy Castle 1.85.2、Flyway 11.14.1；密码校准与初始化说明见上文链接。`git diff --check` 通过。本次未重启现有六服务或重新运行全底座 `smoke-test.ps1`；注册、端口及路由未变。登录/共享会话的实现与验收见下方任务 #7 记录。

2026-11-04，任务 #7：经真实网关完成 CSRF、登录、当前身份查询与退出。Java 21.0.11 下执行 `mvnw.cmd -B clean package`，11 个 reactor 项全部 SUCCESS，44 项测试全部通过（0 failure、0 error、0 skipped），总耗时 4 分 48 秒：管理员初始化 34 项、认证 HTTP 10 项。认证测试通过网关发出真实 HTTP，使用独立 MySQL 8.4.13、Redis 7.4.12 与 Nacos v3.3.0，网关和用户服务为独立进程，管理员由 `--create-admin` 初始化，普通与禁用账户由测试夹具写入。覆盖 CSRF 获取与登录后轮换、登录与 Session ID 轮换、登录前 Cookie 无法取得身份、两个独立会话中退出只撤销当前会话并清除 Cookie、普通用户角色取自数据库、禁用账户仅在密码验证后返回 403、不存在账户与错误密码返回一致的 401、已登录再次登录返回 409、缺失或错误 CSRF 返回 403、重复/额外字段与非字符串/尾随 JSON 返回 400、非 JSON 返回 415、方法 405 与未知路径 404，以及请求标识在网关和用户服务间关联、日志不含密码。首轮运行暴露并修复两个真实缺陷：用户服务私有会话类无法被网关反序列化（最小身份契约移入 `common-core`），以及网关 DispatcherServlet 的 FlashMap 与 request-handled 事件触碰会话，在所有者轮换或撤销后回写只含 `lastAccessedTime` 的残片，使后续旧 Cookie 请求 500（网关改为不读取、不写入会话）。单个测试输出位于 `log/build/auth-slice7-final3.log`，完整构建位于 `log/build/auth-slice7-package2.log`。

同次把 #6 的“正常 Web 启动”测试补上临时 Redis：用户服务现在按共享会话依赖 Redis，该测试原先只准备 MySQL。该命令路径仍不依赖 Redis，未向现有本地库写入数据。本次未重启本地六服务或重新执行全底座 `smoke-test.ps1`（注册、端口与路由未变）；会话并发撤销（#9）、实时身份核验、管理接口、限频与 CORS 未验证。

2026-11-04，任务 #8：新增最小管理员身份接口、实时账户/角色/authEpoch 核验、内部服务身份、头部清理与持久审计。任务在独立 worktree（`feat/admin-auth-isolated`，基底 cd88eb6）完成，迁移时带入的初始 Nacos Config 接入作为后续 #11 基础保留；#10 限频改动已剥离，另行提交。Java 21.0.11 下执行 `mvnw.cmd -B clean package`，11 个 reactor 项全部 SUCCESS，73 项测试全部通过（0 failure、0 error、0 skipped），总耗时 11 分 20 秒：网关过滤器 9 项（身份核验 8、头部清理 1）、管理员初始化 35 项、认证 HTTP 25 项、认证应用服务 4 项。完整输出位于 `log/build/auth8-final-clean-package2.log`。

真实 HTTP 验证使用独立 MySQL 8.4.13、Redis 7.4.12 与 Nacos v3.3.0，网关和用户服务为独立进程，经网关覆盖：管理员/普通用户/游客角色矩阵、身份字段白名单、伪造 `X-User-*`/`X-Internal-*`/转发头、禁用与 `authEpoch` 变化在下一次请求被拒且恢复状态不复活旧会话、直连用户服务的禁用状态/`authEpoch`/角色/CSRF 检查、角色变更对网关与直连会话同时失效、内部接口独立服务身份（浏览器管理员 Session 不能授权）、公共网关隐藏内部与 Actuator 路径、审计不含凭据、内部凭据与 Session/CSRF 不进入应用日志。审查期间发现并修复一个真实授权绕过：受保护路径按原始 URI 判断时，`/api/v1/%61dmin/users/me` 等编码别名可跳过实时核验；现已按 Spring 解码后的路由判断，并新增先失败后通过的 HTTP 回归。另将审计表改为分配式雪花主键、驼峰列与 UTC `DATETIME(3)`，内部核验改用类型化 HTTP Service Client。

管理员命令改为独立 `admin-command.yml` 配置根（local 只含所属数据库），并固定关闭 Nacos 导入与启动诊断；Nacos/Redis 不可达时仍可创建管理员，数据库失败只输出安全标识，不泄露 JDBC 或堆栈。

同次用新构建的六个可执行 JAR（`-Xmx256m`）对现有本地基础设施做了等价六服务检查：Nacos 三个模板发布与读回、六服务健康与健康注册、四库所属账号连接及跨库拒绝、Redis/RabbitMQ 认证、四条网关路由的下游 404 与唯一探针访问记录、公共路径白名单，以及 Nacos 配置生效与请求标识在网关/用户服务日志中的关联，全部通过（`log/build/auth8-smoke2.log`）。用户服务在此使用一次性新库 `bj_user_auth8_smoke`，因为既有本地卷已应用过早期未发布的 V2 校验和；未删除或清空任何既有数据库。本 worktree 中 PowerShell 脚本执行被工作树隔离拦截，故未直接运行 `scripts/smoke-test.ps1`，而是用同一基础设施执行等价的实际制品检查。

未验证边界：会话并发撤销竞争（#9）、限频（#10）、CORS、故障超时与恢复的完整矩阵（后续故障切片）、多实例雪花标识唯一性部署演练、PowerShell 烟测脚本本身的执行。

实测镜像摘要（标签对应的下载制品，生产发布前需重新审查并冻结）：

| 镜像 | RepoDigest SHA-256 |
| --- | --- |
| mysql:8.4.13 | a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902 |
| redis:7.4.12 | b6b6e3dc0c0952737748e55d5d885272840c7e42ba41ab1d5489f693419882f5 |
| rabbitmq:4.1.8-management | bf215c927c885b6d28bc52e02b0f2e1c033f2b439ec946c643834be93312ec4d |
| nacos/nacos-server:v3.3.0 | bf18e41d09efd4e72edc46b010d42dca49f776421413cb2637be1fff0d6ef90f |
