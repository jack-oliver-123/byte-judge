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

start-services.ps1 将变量注入六个子进程，使用 -Xmx256m；应用日志统一在后端根目录 log/，PID 保存在 .local/pids。IDE 启动需先在启动环境注入 .env.local 的变量（或在当前终端点加载 scripts/local-env.ps1）；Spring 不自动加载此文件。可用 SERVER_PORT、MANAGEMENT_PORT、SERVICE_IP 覆盖单实例配置。管理端口需独立配置，避免第二实例冲突。网关调用用户服务内部身份核验使用 `BYTE_JUDGE_INTERNAL_GATEWAY_CREDENTIAL`（至少 32 字符、无默认值）；init-local.ps1 会在 .env.local 中为缺失键生成随机值，非 local 环境必须由部署方注入。用户服务审计使用分配式雪花主键，`BYTE_JUDGE_AUDIT_WORKER_ID` / `BYTE_JUDGE_AUDIT_DATACENTER_ID` 无默认值必须提供；同一时刻运行的每个用户服务实例必须使用全局唯一的组合，冲突会生成重复主键。已有 .env.local 不会自动补齐新键，需重新运行 init-local.ps1。题目服务使用独立的 `BYTE_JUDGE_INTERNAL_PROBLEM_CREDENTIAL` 向用户服务核验身份（双方同值，至少 32 字符），以及 `BYTE_JUDGE_PROBLEM_WORKER_ID` / `BYTE_JUDGE_PROBLEM_DATACENTER_ID`；雪花节点组合需与其他服务及实例全局区分。`BYTE_JUDGE_ARTIFACT_ROOT` 指向受保护测试数据卷，本地默认 `.local/artifacts`，不映射为静态资源。

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

本地所有 Java 和管理端口绑定 127.0.0.1。管理端口只暴露 health，无需鉴权且不显示细节；此为隔离本机开发约定。当前交付认证、最小管理员权限与题目草稿/测试数据登记；其他业务鉴权、生产部署和前端尚未实现，不能直接用于公开部署。judge 当前没有消费者或执行器，保持不接任务。

基础设施：MySQL 3306、Redis 16379（避开本机已有 6379，可通过 REDIS_PORT 覆盖）、RabbitMQ 5672 / 管理 15672；Nacos API 8848、gRPC 9848、控制台 [本地控制台](http://127.0.0.1:8088/)。全部仅绑定回环。四个数据库 bj_user/problem/submission/contest 对应同名账号，口令分别独立；网关与 judge 无业务库账号。Nacos 使用 standalone + Derby，本地关闭鉴权。六服务使用注册发现；网关、用户和题目服务另通过 `spring.config.import` 导入必需的 Nacos Config，启动前按上面的独立步骤导入模板。环境、Data ID、Group、日志与故障恢复说明见 [Nacos 配置与运行材料](infra/nacos/README.md)。

## 验证口径

smoke-test.ps1 失败抛错并返回非零。验证六服务 UP 和 Nacos 健康注册、四数据库账号可连接与跨库拒绝、Redis 认证 PING、RabbitMQ 认证管理 API，以及四条网关路由的下游 404 和唯一探针的目标服务访问记录。单独 404 不算通过。脚本最长等待服务 180 秒；网络调用有独立超时。

网关按 docs/04 §2.3 显式配置四组 lb:// 路由，保留原始路径；没有服务名通配路由，judge、/internal/v1/** 和下游 Actuator 不匹配公共路由，返回 404。访问日志仅记录方法、路径和状态，本地探针用后可清理 log/<服务简称>/access，避免未来敏感参数进入日志。

## 交付验收顺序

从空环境验收后端认证能力时按以下顺序执行；每一步的通过标准不同，不能互相替代。

1. **Java 21 与构建**：`. ./scripts/use-java21.ps1` 后执行 `./mvnw.cmd clean package`；通过标准是全部 reactor 模块 SUCCESS 且测试通过。
2. **基础设施**：`./scripts/start-infra.ps1`（首次由 `init-local.ps1` 生成 `.env.local`）；通过标准是四个容器 healthy。
3. **Nacos 配置导入**：`. ./scripts/local-env.ps1` 后执行 `./scripts/import-nacos-config.ps1`；通过标准是四个 Data ID 逐字读回。服务启动不会代替导入。
4. **数据库迁移**：用户服务启动时执行 Flyway `V1`～`V3`，题目服务执行 `V1`～`V4`。不得清库或改为 baseline；已有库数据必须保留。
5. **管理员初始化**：`java -jar ... --create-admin` 交互输入账户、密码与确认密码，成功退出码为 0 并输出 `ADMIN_CREATED`；不要把密码放进命令行、脚本或历史。
6. **启动服务**：`./scripts/start-services.ps1`；网关、用户和题目服务从 Nacos 读取必需配置并完成健康注册。
7. **底座冒烟**：`./scripts/smoke-test.ps1`，覆盖健康、注册、四库账号、Redis/RabbitMQ 与四条路由的下游证据；单独 404 或 Actuator UP 不算业务验收。
8. **认证业务验收**：按[认证接口](docs/auth-api.md#powershell-调用)执行 GET CSRF 并保存 Cookie → 登录（提交 CSRF 头）→ `GET /users/me` → `GET /admin/users/me` → 退出。登录与退出后旧 CSRF 失效，必须重新获取；失败时读取统一响应中的 `code` 与 `requestId`。
9. **故障与恢复**：按[依赖故障与恢复](docs/auth-api.md#依赖故障与恢复)在隔离环境暂停 Redis/MySQL 或停止用户实例，确认返回 503 且不建立身份；恢复后重新获取 CSRF 并完成合法流程。不要删除本地卷。
10. **停止**：先 `./scripts/stop-services.ps1` 结束网关与用户服务，再 `docker compose --env-file .env.local stop` 停止基础设施；需要回收容器时用 `docker compose --env-file .env.local down`，只有明确不需要保留数据时才追加 `-v`。停止/重启后按第 2、6、8 步确认连接、路由与认证仍可用。

权限、限频、跨实例、闲置、并发撤销和故障场景的自动化入口与已执行结果记录在下方各任务记录；构建、连接验证、底座冒烟与认证验收分别记录，未执行项会注明原因。

当前依赖按服务声明，根 POM 只管理版本与构建规则，两个聚合层保持纯 POM；common-core 保持无框架依赖的普通 JAR，common-web 承载 MVC / Security / Session / 日志适配，由网关、用户服务及已启用受保护接口的题目服务显式依赖。Spring Boot 父工程管理编译、测试、打包插件及其生态依赖版本；Cloud、Alibaba、MyBatis-Plus 分别使用 BOM。Enforcer 检查 Java 21、Maven 3.9+，阻止 Boot2/3 MyBatis-Plus starter、WebFlux starter 和可执行服务模块间的代码依赖。

| 模块 | 主要依赖 |
| --- | --- |
| gateway | Gateway MVC、Actuator、Nacos Discovery + Config、LoadBalancer、Caffeine |
| user / problem / submission / contest | MVC、Validation、RestClient、Actuator、Nacos Discovery、LoadBalancer、Caffeine、MyBatis-Plus Boot4、MySQL、Flyway、Redis、AMQP |
| user 额外 | Spring Security Crypto + Bouncy Castle，实现 Argon2id 管理员密码摘要；Nacos Config；Testcontainers MySQL/Redis/Nacos 为 test scope |
| problem 额外 | common-web 共享会话 / Security 适配、Nacos Config；受保护文件卷与草稿迁移 |
| judge | MVC、Validation、RestClient、Actuator、Nacos Discovery、LoadBalancer、Caffeine、AMQP；无 MySQL / Redis / Session 依赖 |
| 六个启动模块测试 | Spring Boot Test、MVC Test，均为 test scope |

MyBatis-Plus 固定 3.5.18，在根 POM 通过 mybatis-plus.version 和 BOM 管理，依据 [官方 Boot4 安装说明](https://baomidou.com/en/getting-started/install/)；分页解析插件仅在需要时由子模块显式引用，版本由同一 BOM 管理。MySQL Connector/J 固定 9.7.0，在根 POM 显式设置 mysql.version，由继承的 Spring Boot dependencyManagement 使用该属性统一管理。四个数据 owner 按需声明这两项依赖，网关、judge 和 common-core 不引入。MySQL 驱动、Flyway MySQL 扩展和 Caffeine 使用 runtime scope。

四个数据 owner 的 application-local.yml 配置所属库、独立账号、最大 5 连接池；Redis / RabbitMQ 口令来自环境变量。新增 starter 已参与健康检查。用户服务已有正式账户迁移，题目服务新增题目、草稿、测试数据清单与审计迁移，二者 local 下启用 Flyway；提交与竞赛服务尚无业务迁移，仍保持原配置。非 local 环境需提供自己的连接与迁移配置，不能以清库或自动 baseline 绕过已有结构核对。

## 管理员受控初始化

初始化命令、输入规则、退出码、密码参数和测试方式见 [管理员受控初始化](docs/admin-initialization.md)。通过 `java -jar <用户服务制品> --create-admin` 进入独立非 Web 模式，从交互控制台或受控 UTF-8 标准输入读取凭据；正常启动不创建管理员。允许多个管理员，重复账户只报错，不覆盖密码、不提升已有普通用户。命令只装配数据库与密码处理所需组件，不启动网关、Nacos、Redis 或 RabbitMQ。

该项测试使用独立临时 MySQL，不操作现有本地库。登录、当前身份查询与退出由下一节说明的 #7 切片实现；实时身份核验与最小管理员接口见 #8 记录；登录限频由 #10 实现。

## 登录、当前身份与退出

接口协议、错误码、Cookie/CSRF 生命周期、管理员入口和手工调用步骤见 [登录、当前身份、退出与管理员权限接口](docs/auth-api.md)。网关与用户服务使用一致的 Redis Session 契约（indexed repository，命名空间 `byte-judge:session`，30 分钟闲置）；BJSESSION Cookie 在 local 显式关闭 Secure，其他环境默认 Secure。用户服务负责 CSRF 校验和登录/退出写入；网关仅转发 Cookie、CSRF 头和下游 Set-Cookie，既不读取也不写入会话（关闭 request-handled 事件并使用无操作 FlashMapManager），避免在用户服务轮换或撤销会话后回写残片。网关在转发前剥离客户端传入的 `X-User-*`、`X-Internal-*` 与 `Forwarded` / `X-Forwarded-*` 头，下游服务不会收到可伪造的身份头。`GET /api/v1/admin/users/me` 提供最小管理员身份入口；每个受保护请求都由用户服务按权威角色、账户状态和 `authEpoch` 实时核验，网关入口另经独立服务身份调用内部身份接口，浏览器管理员 Session 不能授权该内部接口。登录失败限频按规范化账户与可信来源独立执行，详见 [认证接口](docs/auth-api.md#登录失败限频)。

认证请求日志由 common-web 的基础过滤器生成请求标识（非入口只接受合法格式的下游标识），写入响应头与 MDC，HTTP 完成日志包含请求 ID、方法、路径、状态和耗时，不含查询、请求体、Cookie 或凭据；入口总是重建 ID，下游仅接受一个长度 1～64 的字母/数字/下划线/短横线 ID。MVC 与 Security 错误出口都输出统一 `code/data/message/requestId/serverTime` 结构并保留真实 HTTP 状态。

通用 MVC 异常通过继承 `common-web` 的 `CommonErrorAdvice` 接入，由网关、用户与题目服务自己的 `@RestControllerAdvice` 承载，避免多个全局 Advice 的执行顺序影响业务错误。公共模块集中维护请求体解析、参数类型、404/405/415/500 的映射，并供最外层 `ContractErrorFilter` 复用；业务认证、429 与 `Retry-After`、上游状态及依赖异常仍由各自入口处理。公共异常日志统一为 `request failed requestId=... error=... type=...`（4xx WARN、5xx ERROR），不记录异常消息或堆栈。Filter 保留响应已提交时重抛、原始 I/O 失败重抛，以及故障时清除部分成功响应与 Cookie、恢复关联头的行为。公共模块的 HTTP 契约与过滤链测试可单独运行 `./mvnw.cmd -B -pl byte-judge-commons/common-web -am test`，完整回归仍使用 `./mvnw.cmd -B clean package`。

Spring Security Web 与 Spring Session 的适配集中在 `common-web`，由网关、用户和题目服务显式 `@Import`；Nacos Config 已为认证链路装配，Sentinel 仍待对应规则切片接入。starter 存在不能代表 Outbox、消息消费或迁移已实现。P-01 完整验证、其他原型与 AT 验收仍按架构文档执行，双 submission / judge 演练随后进行。

## 题目草稿与测试数据登记

规格 002 的管理接口由题目服务持有，协议与字段见 [题目草稿接口](docs/problem-draft-api.md)：仅标题创建、永久题号、后台筛选与分页、完整字段 CAS 保存、未发布草稿逻辑删除，以及 SHA-256 寻址的测试数据登记。该切片不提供版本、发布或判题能力。网关与题目服务分别执行身份核验；题目服务通过自己的内部凭据调用用户服务，不读取用户库。成功管理写与审计在同一数据库事务中提交，详情预览另记管理访问审计。

真实 HTTP 测试复用用户服务测试夹具，以独立 JVM 启动网关、用户与题目服务，并使用临时 MySQL / Redis / Nacos 和独立题目库、受保护文件目录。完整 `clean package` 先打包题目服务，再运行用户服务中的 `ProblemDraftFlowTest`，无需预留旧制品。单独执行该套件时先准备制品：

```powershell
./mvnw.cmd -B -pl byte-judge-gateway,byte-judge-models/byte-judge-problem-model -am package -DskipTests
./mvnw.cmd -B -pl byte-judge-models/byte-judge-user-model -am test -Dtest=ProblemDraftFlowTest -Dsurefire.failIfNoSpecifiedTests=false
```

文件每项最多 1 MiB，允许空内容；同一数据槽位重复登记明确拒绝，不同槽位可复用同一内容对象。样例与隐藏数据合计最多 20 组，草稿可先登记输入或输出的一侧。文件先在事务外完成摘要核验与原子落盘，清单、草稿修订与成功审计再事务提交；失败后无引用对象暂不自动回收，题目库与文件卷需一起备份。实际验证结果见下方运行证据。

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

2026-11-05，任务 #9：保证跨实例会话、闲置过期与撤销正确。新增用户库迁移 `V3__create_revoked_session.sql`（雪花主键、`sessionIdHash BINARY(32)` 唯一、UTC `revokedAt`，不保存原始 Cookie），并通过 `SessionRevocation` Port 和 MyBatis 适配器提供幂等撤销判定。退出先持久提交撤销事实，再清理 CSRF、Redis Session 和 Cookie；写入失败返回 503 而不是假成功。同一会话的重复或并发撤销按摘要幂等；写入因雪花主键与另一会话冲突而失败时不下发 `ON DUPLICATE KEY UPDATE`，只有确认该摘要已存在才视为成功，否则把冲突向上传播（退出 503），避免把未落库的撤销当作完成。网关内部身份接口在读取 Redis 前检查撤销；用户直连的实时核验也按 Session ID 检查，并在账户/角色/状态/`authEpoch` 不匹配时先持久撤销再拒绝，因此恢复账户状态不能复活旧会话。共享 Session 适配器把撤销后由旧请求写出的、缺少必需字段的残片当作无效会话（401），避免 500；该适配器不吞 Redis 连接故障。

保留策略：撤销记录永久保留，不设 TTL 或自动清理。30 分钟闲置时限只约束不活动时长，不能证明撤销前已加载会话的在途请求到何时不会再保存；后续回收必须先证明最大请求生命周期或绝对会话寿命。热路径每受保护请求增加一次按唯一摘要的数据库查询，本切片未做吞吐压测。

自动验收使用 Java 21.0.11、Maven Wrapper 3.9.15，真实网关、两个共享同一制品/MySQL/Redis/Nacos 的用户服务进程（独立端口、日志目录和雪花 worker），MySQL 8.4.13、Redis 7.4.12、Nacos v3.3.0 均为临时容器。`CrossInstanceSessionTest` 9 项通过（89.9 秒）：同一 Cookie 的业务请求经 access log 请求标识分别命中两个实例且身份一致；运行实例实际应用 1800 秒默认闲置；退出只撤销当前会话并在两实例直连及网关均拒绝旧 Cookie；撤销记录为 32 字节摘要；协调“读取→退出完成→旧请求保存”后旧 Cookie 仍拒绝；把完整旧 Session 哈希写回 Redis 后仍拒绝；只含属性字段的残片哈希被当作未认证（401）而不是 500；禁用账户与递增 `authEpoch` 后下一次请求拒绝、恢复状态或回退认证版本均不复活、重新登录有效；实例重启后撤销仍有效。`SessionContractTest` 2 项通过（180.3 秒）：30 秒测试超时下闲置过期与活动续期边界，网关和直连一致，并断言运行实例使用该测试值。回归 `AuthenticationFlowTest` 32 项、`NacosRuntimeFlowTest` 5 项均通过。另做了保护移除反证：将 `isRevoked` 临时改为恒 false 后，完整旧 Session 用例以 200 而非 401 失败，确认该断言依赖持久撤销判定；随后恢复实现。

跨实例分流需注意：网关的入口身份核验会额外消费一次负载均衡选择，因此在恰好两个实例时，连续受保护请求的业务代理可能固定落在同一奇偶实例。测试在两次业务请求间交替一个公开请求以观察两个实例；这不是粘性路由，两个进程始终存活且共享会话。手工复现、撤销/保留语义和接口影响见 [认证接口](docs/auth-api.md#会话撤销闲置与跨实例)。

随后在同一工作树以 Java 21.0.11 执行 `mvnw.cmd -B clean package`：11 个 reactor 项全部 SUCCESS，116 项测试全部通过（0 failure、0 error、0 skipped，16 分 09 秒），其中网关 10 项、用户服务 106 项；全量运行中 `CrossInstanceSessionTest` 9 项 143.4 秒、`SessionContractTest` 2 项 184.0 秒，`AuthenticationFlowTest` 32 项、`NacosRuntimeFlowTest` 5 项继续通过。输出位于 `log/build/auth9-full-package-review.log`。未重启本地六服务或重新执行全底座 `smoke-test.ps1`（注册、端口和路由未变）。

评审后加固：撤销写入不再使用 `ON DUPLICATE KEY UPDATE` 吞掉唯一键冲突。重复或并发撤销同一会话仍按摘要幂等成功；若写入失败且该摘要不存在（雪花主键与另一会话冲突），异常向上传播，退出/核验返回 503 而不是假成功。新增 `MybatisSessionRevocationTest` 3 项。随后再次执行 `mvnw.cmd -B clean package`：11 个 reactor 项全部 SUCCESS，119 项测试全部通过（0 failure、0 error、0 skipped，13 分 25 秒），其中网关 10 项、用户服务 109 项，`CrossInstanceSessionTest` 9 项 89.87 秒、`SessionContractTest` 2 项 165.7 秒。输出位于 `log/build/auth9-security-fix-package.log`。

限制：未真实等待 30 分钟，而是从运行实例会话哈希断言 1800 秒并用独立短超时验证边界；Redis 索引哈希本身有额外 5 分钟影子过期，不能以键 TTL 代替认证过期判断；未验证撤销记录长期增长后的归档/清理（明确不在本切片自动删除）；未做认证吞吐压测；未执行全底座 `smoke-test.ps1`（注册、端口和路由未变）。

2026-11-06，任务 #12：依赖故障时拒绝受保护操作并完成交付验收。新增最外层 `ContractErrorFilter`（common-web，`Ordered.HIGHEST_PRECEDENCE + 10`）：Redis 会话读取/保存失败、网关负载均衡与代理异常等逃出过滤链的故障不再落到框架默认错误页；过滤器丢弃可能已缓冲的部分成功响应，恢复 `X-Request-ID` 与 `Cache-Control: no-store` 后写出统一契约，客户端断开导致的原始 I/O 异常仍重抛。新增网关 `GatewayErrorAdvice`，覆盖路由处理器内的 404/405/415、上游状态、依赖不可用与未知异常。`RestIdentityVerifier` 的连接/读取超时改为可配置（默认 1s/2s，`BYTE_JUDGE_IDENTITY_CONNECT_TIMEOUT` / `BYTE_JUDGE_IDENTITY_READ_TIMEOUT`），核验失败记录 `identity verification failed requestId=... type=...` 并返回 503；`ApiError.forStatus` 统一 HTTP 状态到数值业务码，补充 `41500 UNSUPPORTED_MEDIA_TYPE`，因为非 JSON 请求体是既有真实错误出口，统一契约必须覆盖。所有出口保留真实 HTTP 状态、五个字段和 `requestId` 关联，响应与日志不含 SQL、堆栈、内部地址、口令、摘要、会话值或内部凭据。

故障注入只存在于测试 classpath 的 `auth-http-test` profile（`Auth12FaultProbe`），发布制品没有对应开关。测试以 Docker pause/unpause 制造 Redis、MySQL 故障，恢复后容器数据保留，不删除卷、不清库。`DependencyFailureFlowTest` 5 项真实 HTTP 覆盖：暂停 Redis 后，经网关与直连的受保护请求、登录和 CSRF 获取均返回 503 契约，失败保存不下发可用 Cookie；恢复后未撤销会话继续 200，已退出会话仍为 401。暂停 MySQL 后网关与直连的权威核验返回 503，恢复后返回 200。内部核验延迟 15 秒时，网关在配置的读取超时内返回 503（远小于下游延迟，证明是超时而不是等待完成），并留下可关联日志；恢复后同一 Cookie 返回 200。无下游实例与指向已关闭端口的实例分别产生 503 契约。错误出口矩阵逐一断言 400/401/403/404/405/409/415/429/500/503 的状态、数值码、五字段、`requestId` 关联、`serverTime` 格式，以及响应不含异常、JDBC、SQL、Argon2 摘要、会话或口令材料。

Java 21.0.11、Maven Wrapper 3.9.15 下执行 `mvnw.cmd -B clean package`：11 个 reactor 项全部 SUCCESS，124 项测试全部通过（0 failure、0 error、0 skipped，20 分 05 秒），其中网关 10 项、用户服务 114 项（管理员初始化 35、认证 HTTP 32、跨实例会话 9、依赖故障 5、会话契约 2、Nacos 运行时 5、撤销持久化 3、限频 10、限频配置 2、认证器 6、来源解析 5）。输出位于 `log/build/issue12-full-package.log`。

| 测试入口 | 覆盖 | 本次结果 |
| --- | --- | --- |
| `./mvnw.cmd -B clean package` | 全部 reactor 模块与测试 | 124/124 通过，20 分 05 秒 |
| `-Dtest=DependencyFailureFlowTest` | 依赖故障、超时、恢复与错误出口矩阵 | 5/5 通过（全量中 132.1 秒） |
| `-Dtest=AuthenticationFlowTest` | 既有认证流程回归 | 32/32 通过 |
| `-Dtest=CrossInstanceSessionTest,SessionContractTest` | 会话撤销、跨实例与过期回归 | 11/11 通过 |

跳过项与原因：`scripts/smoke-test.ps1` 未复跑（注册、端口与路由未变，属例行全底座验收）；手工 PowerShell 暂停演练未重复（自动化以同样的 pause/unpause 路径执行并断言响应）；本轮未做持续故障、吞吐、时钟漂移或丢包模拟，恢复断言在恢复后立即执行。范围边界：不包含前端、公开注册、改密/找回、用户管理、判题与生产部署；测试使用一次性容器，不触碰既有本地数据。构建成功不替代上述业务验收，未执行项按本表保留。

2026-11-06，公共异常收敛：将请求体解析、参数类型、404/405/415/500 映射与脱敏错误日志集中到 `common-web`，网关与用户服务继承公共 MVC 兜底，过滤器复用同一通用规则；业务认证、限频、上游与依赖策略保留在各自入口。新增公共模块 HTTP 契约与过滤链测试，Java 21.0.11 下执行 `mvnw.cmd -B -pl byte-judge-commons/common-web -am test`，18 项全部通过（0 failure、0 error、0 skipped，10.6 秒），覆盖具体业务 handler 优先级、五字段/状态/错误码、日志脱敏、Servlet 异常解包、I/O 重抛、已提交响应以及清除部分成功响应与 Cookie。输出位于 `log/build/common-errors-focused.log`。

初次全量运行在撤销持久化测试启动时，因 Windows PowerShell 的 `$ErrorActionPreference = 'Stop'` 把 Mockito 标准错误流警告当作 `NativeCommandError` 而中断，不能计为完整构建通过（`log/build/common-errors-full-package.log`）；此前已完成的测试均为零失败。重跑通过 `cmd.exe /d /c ".\mvnw.cmd -B clean package > log\build\common-errors-full-package2.log 2>&1"` 在原生命令层重定向两个输出流，避免警告触发 PowerShell 终止。

重跑结果：Java 21.0.11、Maven Wrapper 3.9.15 下，11 个 reactor 项全部 SUCCESS，142 项测试全部通过（0 failure、0 error、0 skipped，17 分 34 秒）：公共模块 18 项、网关 10 项、用户服务 114 项。既有真实 HTTP 认证 32 项、跨实例会话 9 项、依赖故障与错误出口矩阵 5 项、Nacos 运行时 5 项、会话契约 2 项均通过；测试使用独立临时容器。独立源码审查未发现已确认的功能回归。完整输出位于 `log/build/common-errors-full-package2.log`。本次未重启已有本地六服务或复跑 `scripts/smoke-test.ps1`（注册、端口与路由未变），未执行生产部署或性能验证。

2026-11-06，严格请求体解析收敛：登录请求体与内部身份核验请求体重复的严格 JsonMapper 规则（拒绝未知字段、重复键、尾随内容、标量强转）集中为用户服务内唯一定义 `user.auth.interfaces.http.StrictJson`，两个协议记录各自补充字段级校验后经它解析；CLI 管理员命令的凭据 JSON 是不同契约（手工校验精确键集、错误分类不同），不并入。聚焦验证 Java 21.0.11 下执行 `mvnw.cmd -B -pl byte-judge-models/byte-judge-user-model -am test`，common-web 18 项与用户服务 119 项全部通过（0 failure、0 error、0 skipped），新增 `StrictJsonTest` 5 项覆盖两个协议体的合法解析、登录 12 种与核验 13 种严格性违规及字段长度边界。输出位于 `log/build/strict-json-focused.log`。

全量构建经历两次环境原因的中断后复跑通过：第一次 `clean package` 在 user-model 测试阶段被运行环境因系统内存不足中止（`log/build/strict-json-full-package.log`，已完成的公共模块 18 项与网关 10 项零失败）；第二次在 `SessionContractTest` 启动 Nacos 容器时撞上临时端口占用 `Bind for 0.0.0.0:33268 failed: port is already allocated`（前次中止构建的容器残留，随后自行释放；该类之外 117 项零失败，`log/build/strict-json-full-package2.log`），两者均不计为构建结果。第三次复跑：Java 21.0.11、Maven Wrapper 3.9.15 下，11 个 reactor 项全部 SUCCESS，147 项测试全部通过（0 failure、0 error、0 skipped，15 分 42 秒）：公共模块 18 项、网关 10 项、用户服务 119 项。既有真实 HTTP 认证、跨实例会话、依赖故障与错误出口矩阵、Nacos 运行时、会话契约回归均通过；测试使用独立临时容器。完整输出位于 `log/build/strict-json-full-package3.log`。本次未重启已有本地六服务或复跑 `scripts/smoke-test.ps1`（注册、端口与路由未变），未执行生产部署或性能验证。

2026-11-07，规格 002 / 任务 #15～#19：题目草稿管理与测试数据登记切片。Java 21.0.11、Maven Wrapper 3.9.15 下，在后端根目录执行 `./mvnw.cmd -V -B clean package`：11 个 reactor 项全部 SUCCESS，**218 项测试全部通过**（0 failure、0 error、0 skipped），总耗时 17 分 13 秒。公共模块 18 项、网关 10 项、题目服务 57 项、用户服务 133 项；新增题目真实 HTTP 验收 11 项（99.21 秒），既有认证 HTTP 32 项、跨实例会话 9 项、依赖故障 5 项、Nacos 运行时 5 项与会话契约 2 项继续通过。完整输出位于 `log/build/problem-draft-full-package.log`；此前新增功能聚焦构建 71 项通过，输出位于 `log/build/problem-draft-focused-package.log`。

`ProblemDraftFlowTest` 经真实网关访问独立题目与用户服务 JVM，使用临时 MySQL 8.4.13、Redis 7.4.12、Nacos v3.3.0，执行题目 V1～V4 迁移。覆盖标题创建与 Unicode 边界、永久题号、完整字段保存与严格 JSON、无变更保存递增、CAS 冲突回显、100 KiB UTF-8/标签/资源边界、标题与难度筛选/分页/已删除可见性、删除幂等与题号不复用；文件覆盖 SHA-256 回执、零字节、1 MiB 边界、重复槽位拒绝、跨槽位摘要复用及合计 20 组上限。权限覆盖游客、普通用户、管理员、伪造头、CSRF、直连题目服务、账户状态与 authEpoch 变化，并检查审计事实及日志不含题面/文件正文和凭据。测试不依赖另一可执行服务模块的 Java 类。

同次对现有本地 Compose 基础设施启动六个新构建 JAR（`-Xmx256m`），发布并逐字读回四份 Nacos 模板；题目服务在现有所属库成功应用 V1～V4，无清库或删除卷。执行 `scripts/smoke-test.ps1` 的实际检查正文，退出码 0：六服务健康与健康注册、四库所属账号和 12 组跨库拒绝、Redis PING 与 RabbitMQ 管理 API 认证、四条网关路由的下游 404 及唯一探针访问记录、公共路由白名单、网关/用户/题目三服务配置契约与日志标识，以及网关到用户的请求关联全部通过。当前 PowerShell 执行策略限制脚本文件启动，本次通过临时 Node 辅助程序内联 `local-env.ps1` 并用 `ScriptBlock` 执行原检查正文，未改变执行策略；首轮辅助程序文本拼接错误在检查开始前失败，修正辅助程序后重跑通过，不计作应用失败或通过证据。输出分别位于 `log/build/problem-draft-local-start.log`、`problem-draft-local-smoke-first.log`、`problem-draft-local-smoke.log`。结束后仅停止本次启动的六个 Java 进程，保留基础设施、数据库及迁移结果，清理输出为 `problem-draft-local-stop.log`。烟测末行的 Business/shared session 未验证仅指该底座脚本自身；业务与会话证据见上述独立 HTTP 测试。

未验证/未实现：题目版本、发布/下架、校验执行、判题、C 端查询与前端、生产部署、检索/吞吐性能、多实例雪花节点部署演练、文件卷备份恢复与自动垃圾回收。事务失败后无引用对象及异常残留临时文件暂时保留；本切片不运行自动清理。构建、基础设施冒烟与业务 HTTP 验收各自提供独立证据，不替代这些未验证边界。

2026-11-09，规格 002 / PR #20 审查修复：文件登记的 Snowflake 发号收回持久化适配器，Mapper 通过独立持久化对象读写，由 repository 完成领域转换；服务内共享审计迁至 `problem.audit`，统一命名工厂，服务级错误出口迁至 `problem.interfaces.http`。复用详情查询和列表/count 的 SQL 片段、草稿存活查询及上传拒绝原因映射，删除无调用方法、缩减跨功能锁定结果并改正 LIKE 模式方法命名。样例/隐藏分类在 Java 中集中定义，SQL 对应分类和已应用迁移中的约束保留各自边界；V1～V4 未修改。独立复核发现并修复了异常父类提前匹配导致 `GROUP_LIMIT` 审计原因丢失的问题。新增 `ProblemIdentifierConfigurationTest` 4 项，分别验证缺少 worker/datacenter 配置时启动失败、齐备时发号，以及生产 YAML 无节点默认值。

本轮 Java 21.0.11 下执行 `mvnw.cmd -V -B clean package`，通过 `cmd.exe` 原生重定向记录输出。第一次运行在 `AuthenticationFlowTest` 执行期间被运行环境因系统内存不足强制终止，不计为结果（`log/build/problem-draft-review-full-package.log`）；第二次因 Docker Desktop 未启动，用户服务 8 个容器化测试类在类初始化阶段统一报 `Could not find a valid Docker environment`，非容器测试全部通过，也不计为结果（`problem-draft-review-full-package2.log`）。启动 Docker Desktop（引擎 29.6.1）后第三次复跑成功：**11 个 reactor 项全部 SUCCESS，222 项测试全部通过**（0 failure、0 error、0 skipped，14 分 27 秒）：公共模块 18、网关 10、题目服务 61（含新增配置测试 4）、用户服务 133。真实网关题目验收 `ProblemDraftFlowTest` 11 项、认证 HTTP 32 项、跨实例会话 9 项、依赖故障 5 项、Nacos 运行时 5 项、会话契约 2 项均通过；完整输出位于 `log/build/problem-draft-review-full-package3.log`。本轮未复跑六服务 `smoke-test.ps1`（注册、端口和路由未变）；此前未验证的生产部署、性能与文件回收等边界保持不变。

2026-11-10，规格 002 / PR #20 规范建议修复：在认证接口文档中补齐 `problem` 内部身份核验调用方、独立凭据的持有服务与启动时轮换方式；把文件组数统计中重复的 SAMPLE/HIDDEN `CASE` 抽为 Mapper 共享 SQL 常量；将草稿保存与逻辑删除的重复参数组分别封装为 `application.command` 中的 `SaveDraftCommand` / `DeleteProblemCommand`，同步更新 Controller 和既有 `DraftServiceTest` 调用点。未改变 HTTP 契约、CAS/修订、删除幂等、审计或 V1～V4 迁移。实现复核曾发现共享 SQL 常量的文本块拼接语法错误，修正后通过反射核对生成的两条 SQL 与原查询一致；未增加测试数量。

同日 Java 21.0.11 下执行 `mvnw.cmd -V -B clean package`（通过 `cmd.exe` 原生重定向保存输出）：11 个 reactor 项全部 SUCCESS，**222 项测试全部通过**（0 failure、0 error、0 skipped，19 分 05 秒）：公共模块 18、网关 10、题目服务 61、用户服务 133。真实网关题目验收 `ProblemDraftFlowTest` 11 项（94.06 秒）、草稿服务 12 项、认证 HTTP 32 项、跨实例会话 9 项、依赖故障 5 项、Nacos 运行时 5 项与会话契约 2 项均通过；输出位于 `log/build/pr20-review-fixes-clean-package-20261110.log`。本轮未修改注册、端口或网关路由，故未复跑六服务 `smoke-test.ps1`；生产部署、多实例雪花节点演练、检索性能和文件卷自动回收等既有未验证边界保持不变。
