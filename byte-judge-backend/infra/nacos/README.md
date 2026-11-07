# Nacos 配置、发现与认证链路

本地使用 Nacos Server `v3.3.0`（standalone + Derby），数据保存在 Compose 命名卷。BOM 为 Spring Boot `4.0.9` / Cloud `2025.1.4` / Alibaba `2025.1.0.0`，实际客户端为 `3.1.1`。网关、用户服务和题目服务接入 Config，其他服务保留 Discovery。

本地 API `127.0.0.1:8848`、客户端 gRPC `9848`、控制台 <http://127.0.0.1:8088/>。gRPC 宿主端口必须等于 API 端口加 1000；代理和防火墙也须允许该端口。本地关闭客户端鉴权，端口仅绑定回环；管理 API 的身份检查仍独立生效。这一配置只用于宿主机 Java 服务的隔离开发环境。

## 配置组织与边界

| Data ID | 导入者 | 内容与必需值 |
| --- | --- | --- |
| `byte-judge-common.yml` | gateway、user、problem | 日志级别/模式；必需 `byte-judge.config.contract=auth-infra-v1` |
| `byte-judge-gateway.yml` | gateway | 必需 `byte-judge.web.ingress=true`；网关文件日志服务标记 |
| `byte-judge-user-model.yml` | user | 必需 `byte-judge.web.ingress=false`；用户文件日志服务标记 |

| `byte-judge-problem-model.yml` | problem | 必需 `byte-judge.web.ingress=false`；题目文件日志服务标记 |

源文件位于 [config](config/)。三服务使用 `spring.config.import`，没有 bootstrap 配置，也没有 `optional:`。该版客户端遇到不存在的 Data ID 或部分连接故障可能只返回空属性源；应用因此显式校验公共契约及 ingress 值，不能只依赖 import 的写法证明启动失败。配置值由受控维护者管理，不接受浏览器输入。

环境隔离建议使用不同 Namespace，Data ID 保持不变；Namespace 参数使用实际 ID，不能填显示名称。默认本地配置/发现均为 public，Group 均为 `BYTE_JUDGE`。可通过 `NACOS_CONFIG_NAMESPACE` / `NACOS_CONFIG_GROUP` 和 `NACOS_DISCOVERY_NAMESPACE` / `NACOS_DISCOVERY_GROUP` 分别选择。生产、测试使用独立环境及凭据；Namespace/Group 是组织边界，不能代替管理面权限与网络隔离。

`NACOS_SERVER_ADDR` 在受控环境或文件中提供；Nacos 登录凭据同样通过部署环境中的 `spring.cloud.nacos.config.*` / `spring.cloud.nacos.discovery.*` 属性注入。MySQL、Redis、RabbitMQ、内部调用凭据仍由环境提供，不放进这些共享模板。服务端 token/identity 由 `init-local.ps1` 生成并保留在忽略的 `.env.local`；客户端不需要把管理身份放入 Nacos 文档。

## 导入与启动

在后端根目录的受信任 PowerShell 环境执行：

```powershell
. ./scripts/use-java21.ps1
./mvnw.cmd clean package
./scripts/start-infra.ps1
. ./scripts/local-env.ps1
./scripts/import-nacos-config.ps1
./scripts/start-services.ps1
./scripts/smoke-test.ps1
```

导入是显式维护步骤，服务启动不会覆盖 Nacos 中的配置。再次执行导入会以当前模板更新这四个 Data ID，操作前核对目标 Namespace/Group 及环境；修改配置后重启网关、用户和题目服务。所有导入均为 `refreshEnabled=false`，本切片不承诺通用动态刷新。

本地导入脚本通过 Nacos v3 管理 POST 写入、v3 客户端 GET 逐字读回校验，并对发布后的短暂可见性延迟做有界重试。成功只打印 Data ID、Group、Namespace 和 UTF-8 字节数；失败不转发服务端原始响应、配置正文或身份头。Group/Namespace 会进行 URL 编码。旧 v1 Config API 在目标镜像返回 404。

该脚本只接受回环地址，使用本地受控的服务端管理身份。远程环境应由部署方通过 TLS、最小权限管理账户及其批准的管理入口导入同名模板，不将本地 server identity 发送到远程地址。仓库不自动修改或绕过宿主 PowerShell 执行策略。

## 请求关联与日志

网关始终重新生成 UUID 请求 ID，覆盖外部 `X-Request-ID`；用户服务只接受单个 `[A-Za-z0-9_-]{1,64}` 上游 ID，缺失、格式不符、过长或重复头均重新生成。ID 写入响应 `X-Request-ID`、统一响应体和 SLF4J MDC `requestId`，并随真实代理请求传播。这里的 ID 只用于关联，不承担身份认证。

应用日志为 Logback 固定键值文本。文件行有时间、级别、线程、`service`、MDC `requestId`、logger；HTTP 完成事件包含 `requestId/method/path/status/durationMs`。正常请求及预期 4xx 为 INFO，5xx 或逃出过滤链的异常为 WARN；不附请求体或异常原文。请求结束在 finally 清理 MDC，即使日志输出本身失败也执行清理。

保留每服务 `log/<service>/application.log`，单文件 10 MB、历史 7 天、总归档 100 MB。控制台、Tomcat 访问日志和 Nacos 客户端日志目录见后端 README。Tomcat 只记录方法、路径、状态；测试夹具另记录响应请求 ID。日志不记录查询参数、Cookie、Session、CSRF、密码、摘要或内部凭据；API 路径本身也不得携带秘密。不要为排错开启 HTTP 请求详情、SQL 参数或敏感配置的 DEBUG/TRACE 输出。

例如以响应头取得的 ID 查找两端日志：

```powershell
$requestId = '<响应中的 X-Request-ID>'
Select-String -Path ./log/gateway/application.log,./log/user/application.log -SimpleMatch "requestId=$requestId"
```

用户服务继续负责认证审计表。日志 ID 可与该 owner 的审计 requestId 对照；本切片没有新增审计查询接口、追踪平台或共享审计写库。

## 故障与恢复

| 状态 | 处理与诊断 |
| --- | --- |
| 缺少服务文档，且没有有效快照 | 客户端可能警告空配置；缺少 `byte-judge.web.ingress` 导致进程失败，不使用安全相关默认值 |
| 缺少公共文档，且没有有效快照 | 缺少 `byte-judge.config.contract` 导致失败；不允许静默失去公共日志契约 |
| 首次启动连接不可用，无快照 | 必需配置无法取得，启动失败；先核对地址、API/gRPC 端口和 Namespace/Group |
| 有效本地配置快照，Nacos 不可用 | 配置可从快照读取；这不保证完整服务能启动，正常注册仍可能失败 |
| 已运行服务与 Nacos 断连 | 已加载配置保持；已有发现缓存可继续调用存活目标，不保证缓存始终可用或新目标可发现 |
| 唯一用户实例停止 | 已缓存地址也无法提供认证，调用返回 5xx；不切到硬编码备用实例 |
| Nacos/实例恢复 | 等待 readiness、重新注册和发现更新，确认健康实例列表后以实际 HTTP 再验证 |
| 缺失配置恢复 | 重新导入、逐字读回，再重启失败进程；不要依赖本切片的动态刷新 |

Nacos 配置快照位于启动 JVM 的 `user.home/nacos/config/` 下，按服务端地址、Namespace 和 Group 分区。测试使用每次运行专属 `-Duser.home`，避免其他会话的缓存掩盖首次启动失败。手工演练也应使用新建的隔离目录；不要删除现有用户目录或生产快照来制造“无缓存”。配置快照和发现缓存是两种机制，读取快照成功不能证明能向不可用的注册中心注册。

故障演练只暂停或停止独立测试容器，不删除现有 Compose 卷。自动化用例以暂停容器模拟运行中与注册/配置中心断连，恢复后检查 `/nacos/v3/client/ns/instance/list`，健康注册记录与经网关的真实 CSRF/登录/身份调用共同组成证据。Nacos 自身的完整冷重启恢复未纳入本切片，不能据暂停恢复推断已验证服务端重启。服务端控制台截图、仅有健康检查或只见下游 404 均不足。

## 可复现验证

从后端根目录执行以下命令。完整 reactor 会先生成网关运行 classpath；只跑用户模块前须先 `-pl byte-judge-gateway -am -DskipTests package`：

```powershell
./mvnw.cmd -B -pl byte-judge-gateway,byte-judge-models/byte-judge-user-model -am '-Dtest=NacosRuntimeFlowTest,AuthenticationFlowTest#requiredNacosConfigurationControlsIngressAndStructuredLogs+concurrentRequestsKeepTheirOwnCorrelationId+missingRequiredNacosConfigurationStopsStartupWithoutInsecureDefaults+unreachableNacosStopsStartupInsteadOfSilentlyUsingDefaults' -Dsurefire.failIfNoSpecifiedTests=false package
```

`AuthenticationFlowTest` 通过独立 MySQL/Redis/Nacos 与两个 Java 进程验证真实 HTTP。`NacosRuntimeFlowTest` 复用该夹具、独立启动另一套容器，演练公共配置缺失、服务配置恢复、Nacos 断连/重连、配置快照、实例摘除/恢复和敏感日志检查。MDC 测试并发发出八个请求，同时核对两端的响应 ID/日志字段，并用仅存在于 test classpath 的 Servlet 生命周期监听器在整个过滤链结束后检查 MDC 已空。测试监听器不打包进可执行 JAR。

实测输出位于忽略的 `log/build/`，最终通过数、故障结果及尚未完成的检查统一记录在 [后端 README](../../README.md)。当前范围不包括 Nacos 集群、生产 TLS/RBAC 部署、全配置热更新、长时间断网缓存保证或完整业务验收。
