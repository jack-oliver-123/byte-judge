# Byte Judge 后端

Java 21 / Maven 3.9.15；Spring Boot 4.0.9、Cloud 2025.1.4、Alibaba 2025.1.0.0。根工程聚合 commons、gateway、models，共 11 个 POM；common-core 与 common-web 为普通 JAR，网关和五个业务服务为可执行 JAR。

## 本地启动（PowerShell）

要求 Docker Desktop 正常运行，空闲端口见下表。命令在后端根目录执行：

```powershell
. ./scripts/use-java21.ps1
./mvnw.cmd clean package
./scripts/start-infra.ps1
. ./scripts/local-env.ps1
./scripts/import-nacos-config.ps1
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

基础设施：MySQL 3306、Redis 16379（避开本机已有 6379，可通过 REDIS_PORT 覆盖）、RabbitMQ 5672 / 管理 15672；Nacos API 8848、gRPC 9848、控制台 [本地控制台](http://127.0.0.1:8088/)。全部仅绑定回环。四个数据库 bj_user/problem/submission/contest 对应同名账号，口令分别独立；网关与 judge 无业务库账号。Nacos 使用 standalone + Derby，本地关闭鉴权。六服务使用注册发现；网关和用户服务另通过 `spring.config.import` 导入必需的 Nacos Config，启动前按上面的独立步骤导入模板。环境、Data ID、Group、日志与故障恢复说明见 [Nacos 配置与运行材料](infra/nacos/README.md)。

## 验证口径

smoke-test.ps1 失败抛错并返回非零。验证六服务 UP 和 Nacos 健康注册、四数据库账号可连接与跨库拒绝、Redis 认证 PING、RabbitMQ 认证管理 API，以及四条网关路由的下游 404 和唯一探针的目标服务访问记录。单独 404 不算通过。脚本最长等待服务 180 秒；网络调用有独立超时。

网关按 docs/04 §2.3 显式配置四组 lb:// 路由，保留原始路径；没有服务名通配路由，judge、/internal/v1/** 和下游 Actuator 不匹配公共路由，返回 404。访问日志仅记录方法、路径和状态，本地探针用后可清理 log/<服务简称>/access，避免未来敏感参数进入日志。

当前依赖按服务声明，根 POM 只管理版本与构建规则，两个聚合层保持纯 POM；common-core 保持无框架依赖的普通 JAR，common-web 承载 MVC / Security / Session / 日志适配且只被网关与用户服务依赖。Spring Boot 父工程管理编译、测试、打包插件及其生态依赖版本；Cloud、Alibaba、MyBatis-Plus 分别使用 BOM。Enforcer 检查 Java 21、Maven 3.9+，阻止 Boot2/3 MyBatis-Plus starter、WebFlux starter 和可执行服务模块间的代码依赖。

| 模块 | 主要依赖 |
| --- | --- |
| gateway | Gateway MVC、Actuator、Nacos Discovery + Config、LoadBalancer、Caffeine |
| user / problem / submission / contest | MVC、Validation、RestClient、Actuator、Nacos Discovery、LoadBalancer、Caffeine、MyBatis-Plus Boot4、MySQL、Flyway、Redis、AMQP |
| user 额外 | Spring Security Crypto + Bouncy Castle，实现 Argon2id 管理员密码摘要；Nacos Config；Testcontainers MySQL/Redis/Nacos 为 test scope |
| judge | MVC、Validation、RestClient、Actuator、Nacos Discovery、LoadBalancer、Caffeine、AMQP；无 MySQL / Redis / Session 依赖 |
| 六个启动模块测试 | Spring Boot Test、MVC Test，均为 test scope |

MyBatis-Plus 固定 3.5.18，在根 POM 通过 mybatis-plus.version 和 BOM 管理，依据 [官方 Boot4 安装说明](https://baomidou.com/en/getting-started/install/)；分页解析插件仅在需要时由子模块显式引用，版本由同一 BOM 管理。MySQL Connector/J 固定 9.7.0，在根 POM 显式设置 mysql.version，由继承的 Spring Boot dependencyManagement 使用该属性统一管理。四个数据 owner 按需声明这两项依赖，网关、judge 和 common-core 不引入。MySQL 驱动、Flyway MySQL 扩展和 Caffeine 使用 runtime scope。

四个数据 owner 的 application-local.yml 配置所属库、独立账号、最大 5 连接池；Redis / RabbitMQ 口令来自环境变量。新增 starter 已参与健康检查。用户服务已有正式账户迁移，local 下启用 Flyway；另外三个数据 owner 尚无业务迁移，仍保持原配置。非 local 环境需提供自己的连接与迁移配置，不能以清库或自动 baseline 绕过已有结构核对。

## 管理员受控初始化

初始化命令、输入规则、退出码、密码参数和测试方式见 [管理员受控初始化](docs/admin-initialization.md)。通过 `java -jar <用户服务制品> --create-admin` 进入独立非 Web 模式，从交互控制台或受控 UTF-8 标准输入读取凭据；正常启动不创建管理员。允许多个管理员，重复账户只报错，不覆盖密码、不提升已有普通用户。命令只装配数据库与密码处理所需组件，不启动网关、Nacos、Redis 或 RabbitMQ。

该项测试使用独立临时 MySQL，不操作现有本地库。登录、当前身份查询与退出由下一节说明的 #7 切片实现；实时身份核验与最小管理员接口见 #8 记录；登录限频由 #10 实现。

## 登录、当前身份与退出

接口协议、错误码、Cookie/CSRF 生命周期、管理员入口和手工调用步骤见 [登录、当前身份、退出与管理员权限接口](docs/auth-api.md)。网关与用户服务使用一致的 Redis Session 契约（indexed repository，命名空间 `byte-judge:session`，30 分钟闲置）；BJSESSION Cookie 在 local 显式关闭 Secure，其他环境默认 Secure。用户服务负责 CSRF 校验和登录/退出写入；网关仅转发 Cookie、CSRF 头和下游 Set-Cookie，既不读取也不写入会话（关闭 request-handled 事件并使用无操作 FlashMapManager），避免在用户服务轮换或撤销会话后回写残片。网关在转发前剥离客户端传入的 `X-User-*`、`X-Internal-*` 与 `Forwarded` / `X-Forwarded-*` 头，下游服务不会收到可伪造的身份头。`GET /api/v1/admin/users/me` 提供最小管理员身份入口；每个受保护请求都由用户服务按权威角色、账户状态和 `authEpoch` 实时核验，网关入口另经独立服务身份调用内部身份接口，浏览器管理员 Session 不能授权该内部接口。登录失败限频按规范化账户与可信来源独立执行，详见 [认证接口](docs/auth-api.md#登录失败限频)。

认证请求日志由 common-web 的基础过滤器生成请求标识（非入口只接受合法格式的下游标识），写入响应头与 MDC，HTTP 完成日志包含请求 ID、方法、路径、状态和耗时，不含查询、请求体、Cookie 或凭据；入口总是重建 ID，下游仅接受一个长度 1～64 的字母/数字/下划线/短横线 ID。MVC 与 Security 错误出口都输出统一 `code/data/message/requestId/serverTime` 结构并保留真实 HTTP 状态。

Spring Security Web 与 Spring Session 的适配集中在 `common-web`，由网关和用户服务显式 `@Import`；Nacos Config 已为认证链路装配，Sentinel 仍待对应规则切片接入。starter 存在不能代表 Outbox、消息消费或迁移已实现。P-01 完整验证、其他原型与 AT 验收仍按架构文档执行，双 submission / judge 演练随后进行。

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

2026-11-04，任务 #10：登录失败按规范化账户与可信来源独立执行 Redis 原子限频，默认 15 分钟固定窗口内 10 次失败触发 15 分钟限制；第 10 次仍为 401，后续为 `429/42900`，`Retry-After` 与 `data.retryAfterSeconds` 一致。成功不清零，重复受限请求不续期；来源由网关 socket 对端及用户服务显式可信代理边界确定。协议、信任配置和 PowerShell 阈值复现示例见 [认证接口](docs/auth-api.md#登录失败限频)。

同次在独立 worktree `feat/login-rate-limit` 使用 Java 21.0.11、Maven Wrapper 3.9.15：7 项新增真实 HTTP 验收全部通过（0 failure、0 error、0 skipped，2 分 56 秒），覆盖来源跨账户、账户跨实际来源及大小写/空白归一化、20 个并发失败恰好 10 个 401 与 10 个 429、伪造转发/内部来源头、成功不清零、短限制到期恢复且重复 429 不延长，以及非可信来源直连用户服务。网关与用户服务为独立 JVM，MySQL/Redis/Nacos 为独立临时容器；仅限制时长覆盖为 30 秒，阈值/窗口仍为 10/15m。另有 23 项聚焦测试全部通过（21 项用户服务、2 项网关，33 秒），包括真实 Redis 原子计数/TTL/并发、来源解析、生产默认值及 Redis 故障时认证失败关闭。固定窗口测试另以临时滑动窗口变体验证会按预期失败（剩余 4997ms 未小于原窗口 4891ms），确认用例能区分两种语义；生产实现已恢复并通过。

随后在同一 worktree 执行 `mvnw clean package -B`：Java 21.0.11 下 11 个 reactor 项全部 SUCCESS，95 项测试全部通过（0 failure、0 error、0 skipped），总耗时 8 分 16 秒，其中网关 9 项、用户服务 86 项。该次构建同时包含管理员命令独立配置根修复（`--spring.config.name=admin-command`、`--spring.cloud.nacos.config.enabled=false`、`admin-command.yml` / `admin-command-local.yml`），使 admin-command 回归通过。输出保存在 `log/build/issue10-full-package.log`，HTTP、聚焦及窗口反证分别位于 `log/build/issue10-http.log`、`issue10-focused.log`、`issue10-sliding-window-negative.log`。未重启已有六服务或执行全底座 smoke，#10 未改变注册、端口或路由；Redis 故障的完整 HTTP 响应仍由最终集成切片验收。

后续（`c56758b`）：全量构建高负载下暴露两处时序脆弱点——3 秒限制块可能在 10 次串行登录完成前到期，以及 Nacos 管理 API 的 10 秒请求超时。现已把测试限制时长改为 30 秒、到期用例改用缩短 Redis 键 TTL 模拟时间流逝，并将 Nacos 发布/删除请求超时提高到 30 秒；该修复单独落在本分支，随后执行 `mvnw clean package`，11 个 reactor 项全部 SUCCESS，95 项测试全部通过（0 failure、0 error、0 skipped），总耗时 6 分 34 秒，输出位于 `log/build/auth10-backport-full-package.log`。

2026-11-04，任务 #8：新增最小管理员身份接口、实时账户/角色/authEpoch 核验、内部服务身份、头部清理与持久审计。任务先在独立 worktree（`feat/admin-auth-isolated`，基底 cd88eb6）完成，随后合入本集成分支，与 #10 限频并存。该分支构建时 Java 21.0.11 下执行 `mvnw.cmd -B clean package`，11 个 reactor 项全部 SUCCESS，73 项测试全部通过（0 failure、0 error、0 skipped），总耗时 11 分 20 秒：网关过滤器 9 项（身份核验 8、头部清理 1）、管理员初始化 35 项、认证 HTTP 25 项、认证应用服务 4 项。完整输出位于 `log/build/auth8-final-clean-package2.log`。

真实 HTTP 验证使用独立 MySQL 8.4.13、Redis 7.4.12 与 Nacos v3.3.0，网关和用户服务为独立进程，经网关覆盖：管理员/普通用户/游客角色矩阵、身份字段白名单、伪造 `X-User-*`/`X-Internal-*`/转发头、禁用与 `authEpoch` 变化在下一次请求被拒且恢复状态不复活旧会话、直连用户服务的禁用状态/`authEpoch`/角色/CSRF 检查、角色变更对网关与直连会话同时失效、内部接口独立服务身份（浏览器管理员 Session 不能授权）、公共网关隐藏内部与 Actuator 路径、审计不含凭据、内部凭据与 Session/CSRF 不进入应用日志。审查期间发现并修复一个真实授权绕过：受保护路径按原始 URI 判断时，`/api/v1/%61dmin/users/me` 等编码别名可跳过实时核验；现已按 Spring 解码后的路由判断，并新增先失败后通过的 HTTP 回归。另将审计表改为分配式雪花主键、驼峰列与 UTC `DATETIME(3)`，内部核验改用类型化 HTTP Service Client。

管理员命令改为独立 `admin-command.yml` 配置根（local 只含所属数据库），并固定关闭 Nacos 导入与启动诊断；Nacos/Redis 不可达时仍可创建管理员，数据库失败只输出安全标识，不泄露 JDBC 或堆栈。

同次用新构建的六个可执行 JAR（`-Xmx256m`）对现有本地基础设施做了等价六服务检查：Nacos 三个模板发布与读回、六服务健康与健康注册、四库所属账号连接及跨库拒绝、Redis/RabbitMQ 认证、四条网关路由的下游 404 与唯一探针访问记录、公共路径白名单，以及 Nacos 配置生效与请求标识在网关/用户服务日志中的关联，全部通过（`log/build/auth8-smoke2.log`）。用户服务在此使用一次性新库 `bj_user_auth8_smoke`，因为既有本地卷已应用过早期未发布的 V2 校验和；未删除或清空任何既有数据库。本 worktree 中 PowerShell 脚本执行被工作树隔离拦截，故未直接运行 `scripts/smoke-test.ps1`，而是用同一基础设施执行等价的实际制品检查。

合入 #8 与 #10 后，在同一 worktree 执行 `mvnw.cmd -B clean package`：Java 21.0.11 下 11 个 reactor 项全部 SUCCESS，100 项测试全部通过（0 failure、0 error、0 skipped，9 分 05 秒），其中网关 10 项、用户服务 90 项（管理员初始化 35、认证 HTTP 32、认证应用服务 6、限频 12、来源解析 5）。输出位于 `log/build/merge-auth8-package.log`。

随后以含 #10 时序修复的最新 tip（`f9e5c84`）为基底合并 #8 与 #11，并重跑完整 `mvnw.cmd -B clean package`：11 个 reactor 项全部 SUCCESS，105 项测试全部通过（0 failure、0 error、0 skipped，9 分 19 秒），其中网关 10 项、用户服务 95 项（管理员初始化 35、认证 HTTP 32、认证应用服务 6、Nacos 运行时 5、限频 12、来源解析 5）。输出位于 `log/build/merge-auth8-combined-package.log`。合并保留 30 秒测试限制块、TTL 加速到期用例、30 秒 Nacos 管理 API 超时，以及 #11 的 Nacos 运行时与日志关联验收。

未验证边界：CORS、故障超时与恢复的完整矩阵（后续故障切片）、多实例雪花标识唯一性部署演练、PowerShell 烟测脚本本身的执行。会话并发撤销竞争由 #9 记录。

2026-11-05，任务 #11：使用 Nacos 外置配置运行认证流程并关联日志。依赖采用 `spring.config.import`（非 bootstrap），环境、Data ID、Group 组织与非敏感模板、导入步骤、日志关联示例、故障/恢复对照与限制见 [Nacos 配置与运行材料](infra/nacos/README.md)。网关与用户服务通过 `spring.config.import` 导入必需的 `byte-judge-common.yml` 与各自服务文档，并显式 `refreshEnabled=false`，不承诺全量动态刷新；Data ID 不带环境占位符。`import-nacos-config.ps1` 只发布三个命名模板，回环校验、强制 `NACOS_AUTH_IDENTITY_KEY/VALUE`、UTF-8 读回与重试，错误信息与输出不含凭据。WebProtocolConfiguration 对导入契约做 fail-closed 校验，缺失 Data ID（客户端可能返回空源）时启动失败，不使用不安全默认值。

真实 HTTP 链路验收使用独立 MySQL 8.4.13、Redis 7.4.12 与 Nacos v3.3.0 容器，网关与用户服务为独立 JVM：running 认证（CSRF/登录/当前身份）在 Nacos 中断（pause）后继续工作，合法本地快照可完成配置只读启动，但启用发现时 Nacos 中断则非零退出；恢复后两服务重新注册；停掉唯一用户实例返回 5xx（无硬编码降级），重启后恢复 200；删除/重新发布服务文档可再次冷启动。请求标识 MDC 并发隔离与请求结束清理用并发真实 HTTP 验证（8 并发，两服务，生命周期监听器），每请求各日志恰一行 `[requestId=<id>]`、结束仪式为 `[requestId=none]` 与 `remainingMdc=empty`。日志脱敏扫描覆盖密码、Argon2 摘要标记、Cookie、CSRF、内部网关凭据、数据库/Redis 口令与敏感查询值；审计仍归用户服务自有，未搭建审计查询或追踪平台。

Java 21.0.11、Maven Wrapper 3.9.15 下执行 `mvnw clean package -B`，10 个 reactor 项全部 SUCCESS，101 项测试全部通过（0 failure、0 error、0 skipped），总耗时 13 分 19 秒，其中网关 9 项、用户服务 92 项（管理员 35、认证 HTTP 29、Nacos 运行时 5、限频聚焦 12、认证器 6、来源解析 5）。输出保存在 `log/build/nacos-issue11-final-package.log`。管理员命令独立配置根（`admin-command.yml`、`--spring.cloud.nacos.config.enabled=false`）不连接 Nacos/Redis，其“普通 Web 启动”回归测试以 `SPRING_CONFIG_LOCATION=classpath:byte-judge-session.yml` + `SPRING_CLOUD_NACOS_CONFIG_ENABLED=false` 独立于配置中心验证。

未验证/未实现项：Nacos 冷重启（pause 变更来自运行容器）、集群/TLS/RBAC/动态刷新；`smoke-test.ps1` 本次未执行（注册、端口、路由未变，需在例行全底座验收时复跑）。仓库 `.idea/ApifoxUploaderProjectSetting.xml`（基线上已提交）含 Apifox 访问令牌，需从版本库移除并轮换，不在本任务擅改。

实测镜像摘要（标签对应的下载制品，生产发布前需重新审查并冻结）：

| 镜像 | RepoDigest SHA-256 |
| --- | --- |
| mysql:8.4.13 | a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902 |
| redis:7.4.12 | b6b6e3dc0c0952737748e55d5d885272840c7e42ba41ab1d5489f693419882f5 |
| rabbitmq:4.1.8-management | bf215c927c885b6d28bc52e02b0f2e1c033f2b439ec946c643834be93312ec4d |
| nacos/nacos-server:v3.3.0 | bf18e41d09efd4e72edc46b010d42dca49f776421413cb2637be1fff0d6ef90f |

2026-11-05，任务 #9：保证跨实例会话、闲置过期与撤销正确。新增用户库迁移 `V3__create_revoked_session.sql`（雪花主键、`sessionIdHash BINARY(32)` 唯一、UTC `revokedAt`，不保存原始 Cookie），并通过 `SessionRevocation` Port 和 MyBatis 适配器提供幂等撤销判定。退出先持久提交撤销事实，再清理 CSRF、Redis Session 和 Cookie；写入失败返回 503 而不是假成功。网关内部身份接口在读取 Redis 前检查撤销；用户直连的实时核验也按 Session ID 检查，并在账户/角色/状态/`authEpoch` 不匹配时先持久撤销再拒绝，因此恢复账户状态不能复活旧会话。共享 Session 适配器把撤销后由旧请求写出的、缺少必需字段的残片当作无效会话（401），避免 500；该适配器不吞 Redis 连接故障。

保留策略：撤销记录永久保留，不设 TTL 或自动清理。30 分钟闲置时限只约束不活动时长，不能证明撤销前已加载会话的在途请求到何时不会再保存；后续回收必须先证明最大请求生命周期或绝对会话寿命。热路径每受保护请求增加一次按唯一摘要的数据库查询，本切片未做吞吐压测。

自动验收使用 Java 21.0.11、Maven Wrapper 3.9.15，真实网关、两个共享同一制品/MySQL/Redis/Nacos 的用户服务进程（独立端口、日志目录和雪花 worker），MySQL 8.4.13、Redis 7.4.12、Nacos v3.3.0 均为临时容器。`CrossInstanceSessionTest` 9 项通过（89.9 秒）：同一 Cookie 的业务请求经 access log 请求标识分别命中两个实例且身份一致；运行实例实际应用 1800 秒默认闲置；退出只撤销当前会话并在两实例直连及网关均拒绝旧 Cookie；撤销记录为 32 字节摘要；协调“读取→退出完成→旧请求保存”后旧 Cookie 仍拒绝；把完整旧 Session 哈希写回 Redis 后仍拒绝；只含属性字段的残片哈希被当作未认证（401）而不是 500；禁用账户与递增 `authEpoch` 后下一次请求拒绝、恢复状态或回退认证版本均不复活、重新登录有效；实例重启后撤销仍有效。`SessionContractTest` 2 项通过（180.3 秒）：30 秒测试超时下闲置过期与活动续期边界，网关和直连一致，并断言运行实例使用该测试值。回归 `AuthenticationFlowTest` 32 项、`NacosRuntimeFlowTest` 5 项均通过。另做了保护移除反证：将 `isRevoked` 临时改为恒 false 后，完整旧 Session 用例以 200 而非 401 失败，确认该断言依赖持久撤销判定；随后恢复实现。

跨实例分流需注意：网关的入口身份核验会额外消费一次负载均衡选择，因此在恰好两个实例时，连续受保护请求的业务代理可能固定落在同一奇偶实例。测试在两次业务请求间交替一个公开请求以观察两个实例；这不是粘性路由，两个进程始终存活且共享会话。手工复现、撤销/保留语义和接口影响见 [认证接口](docs/auth-api.md#会话撤销闲置与跨实例)。

随后在同一工作树以 Java 21.0.11 执行 `mvnw.cmd -B clean package`：11 个 reactor 项全部 SUCCESS，116 项测试全部通过（0 failure、0 error、0 skipped，16 分 09 秒），其中网关 10 项、用户服务 106 项；全量运行中 `CrossInstanceSessionTest` 9 项 143.4 秒、`SessionContractTest` 2 项 184.0 秒，`AuthenticationFlowTest` 32 项、`NacosRuntimeFlowTest` 5 项继续通过。输出位于 `log/build/auth9-full-package-review.log`。未重启本地六服务或重新执行全底座 `smoke-test.ps1`（注册、端口和路由未变）。

限制：未真实等待 30 分钟，而是从运行实例会话哈希断言 1800 秒并用独立短超时验证边界；Redis 索引哈希本身有额外 5 分钟影子过期，不能以键 TTL 代替认证过期判断；未验证撤销记录长期增长后的归档/清理（明确不在本切片自动删除）；未做认证吞吐压测；未执行全底座 `smoke-test.ps1`（注册、端口和路由未变）。
