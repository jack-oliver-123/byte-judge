# Byte Judge 后端

Java 21 / Maven 3.9.15；Spring Boot 4.0.9、Cloud 2025.1.4、Alibaba 2025.1.0.0。根工程聚合 commons、gateway、models，共 10 个 POM；common-core 为普通 JAR，网关和五个业务服务为可执行 JAR。

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

start-services.ps1 将变量注入六个子进程，使用 -Xmx256m；应用日志统一在后端根目录 log/，PID 保存在 .local/pids。IDE 启动需先在启动环境注入 .env.local 的变量（或在当前终端点加载 scripts/local-env.ps1）；Spring 不自动加载此文件。可用 SERVER_PORT、MANAGEMENT_PORT、SERVICE_IP 覆盖单实例配置。管理端口需独立配置，避免第二实例冲突。

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

本地所有 Java 和管理端口绑定 127.0.0.1。管理端口只暴露 health，无需鉴权且不显示细节；此为隔离本机开发约定。业务鉴权尚待实现，不能直接用于公开部署。judge 当前没有消费者或执行器，保持不接任务。

基础设施：MySQL 3306、Redis 16379（避开本机已有 6379，可通过 REDIS_PORT 覆盖）、RabbitMQ 5672 / 管理 15672；Nacos API 8848、gRPC 9848、控制台 [本地控制台](http://127.0.0.1:8088/)。全部仅绑定回环。四个数据库 bj_user/problem/submission/contest 对应同名账号，口令分别独立；网关与 judge 无业务库账号。Nacos 使用 standalone + Derby，本地关闭鉴权。本轮启用注册发现，配置中心延后；后续通过 spring.config.import 接入。

## 验证口径

smoke-test.ps1 失败抛错并返回非零。验证六服务 UP 和 Nacos 健康注册、四数据库账号可连接与跨库拒绝、Redis 认证 PING、RabbitMQ 认证管理 API，以及四条网关路由的下游 404 和唯一探针的目标服务访问记录。单独 404 不算通过。脚本最长等待服务 180 秒；网络调用有独立超时。

网关按 docs/04 §2.3 显式配置四组 lb:// 路由，保留原始路径；没有服务名通配路由，judge、/internal/v1/** 和下游 Actuator 不匹配公共路由，返回 404。访问日志仅记录方法、路径和状态，本地探针用后可清理 log/<服务简称>/access，避免未来敏感参数进入日志。

当前依赖按服务声明，根 POM 只管理版本与构建规则，两个聚合层保持纯 POM；common-core 保持无框架依赖的普通 JAR。Spring Boot 父工程管理编译、测试、打包插件及其生态依赖版本；Cloud、Alibaba、MyBatis-Plus 分别使用 BOM。Enforcer 检查 Java 21、Maven 3.9+，阻止 Boot2/3 MyBatis-Plus starter、WebFlux starter 和可执行服务模块间的代码依赖。

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

本切片仅实现管理员创建和密码持久化；登录、CSRF、共享 Session、权限及限频仍由后续任务实现。测试使用独立临时 MySQL，不操作现有本地库。

Spring Security Web、Spring Session、Sentinel 和 Nacos Config 在对应认证、共享会话、规则与配置装配阶段接入；本轮没有提前添加会改变默认访问策略的自动配置。starter 存在不能代表 Outbox、消息消费或迁移已实现。P-01 完整验证、其他原型与 AT 验收仍按架构文档执行，双 submission / judge 演练随后进行。

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

2026-11-04：根 POM 显式管理 mysql.version=9.7.0，保留 MyBatis-Plus BOM 3.5.18。Java 21.0.11 下执行 mvnw.cmd -B clean package，10 个模块全部 SUCCESS；产物检查确认四个数据 owner 含 Connector/J 9.7.0 和 MyBatis-Plus Boot4 starter 3.5.18，gateway / judge 不含 MySQL / MyBatis 依赖，common-core 仍为普通 JAR。Windows PowerShell 点加载 use-java21.ps1 时因 java -version 的标准错误输出触发 NativeCommandError，本次在当前进程显式设置同一 JDK 的 JAVA_HOME / PATH，并用 java --version 确认后完成构建；脚本兼容问题尚未修复。当前没有测试类；本次未执行服务启动、数据库 CRUD 或迁移验证。

2026-11-04，任务 #6：新增管理员受控初始化、Argon2id 和正式账户迁移。Java 21.0.11 下执行 `mvnw clean package -B`，10 个 reactor 项全部 SUCCESS，34 项命令/持久化行为测试全部通过（0 failure、0 error、0 skipped），总耗时 3 分 55 秒。测试使用 Testcontainers 2.0.5 和独立 MySQL 8.4.13，覆盖输入边界、Unicode/空白、JSON 换行密码、随机盐、已有普通用户与管理员不覆盖、双进程并发、数据库失败、迁移关闭、普通 Web 启动不建账户及 TRACE 下摘要不泄露。完整输出位于 `log/build/admin-initialization-final-package.log`。

同次对实际可执行 JAR 以 `-Xmx256m` 在另一个临时 MySQL 上验证：创建退出码 0、重复退出码 3、原账户与摘要保持不变、JSON 换行密码创建成功、Flyway 和 Argon2id 存储检查通过。临时容器等待 MySQL 健康后执行并在结束后移除，未向现有本地库写入管理员。制品包含 Security Crypto 7.0.8、Bouncy Castle 1.85.2、Flyway 11.14.1；密码校准与初始化说明见上文链接。`git diff --check` 通过。本次未重启现有六服务或重新运行全底座 `smoke-test.ps1`；注册、端口及路由未变，登录/共享会话等后续验收仍待实现。

实测镜像摘要（标签对应的下载制品，生产发布前需重新审查并冻结）：

| 镜像 | RepoDigest SHA-256 |
| --- | --- |
| mysql:8.4.13 | a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902 |
| redis:7.4.12 | b6b6e3dc0c0952737748e55d5d885272840c7e42ba41ab1d5489f693419882f5 |
| rabbitmq:4.1.8-management | bf215c927c885b6d28bc52e02b0f2e1c033f2b439ec946c643834be93312ec4d |
| nacos/nacos-server:v3.3.0 | bf18e41d09efd4e72edc46b010d42dca49f776421413cb2637be1fff0d6ef90f |
