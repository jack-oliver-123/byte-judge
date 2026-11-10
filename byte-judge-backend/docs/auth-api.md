# 登录、当前身份、退出与管理员权限接口

本文对应 #7、#8、#9、#10、#12。调用入口为本地网关 `http://127.0.0.1:8080`；部署使用同源 HTTPS。管理员先按 [初始化说明](admin-initialization.md) 创建，普通用户由受控测试夹具准备，当前没有公开注册接口。

## 协议

公开接口均使用 `/api/v1` 前缀，响应为 JSON：

```json
{"code":0,"data":{"userId":"1","account":"operator","role":"admin"},"message":"OK","requestId":"opaque-id","serverTime":"2026-11-04T00:00:00.000Z"}
```

`code` 是整数，仅 `0` 表示成功。失败时通常 `data=null`，唯一的例外是登录限频 429，其 `data` 只含 `retryAfterSeconds`；HTTP 状态保持实际错误语义。`userId` 为十进制字符串，`role` 只来自数据库，取 `admin` 或 `user`。响应不包含密码摘要、账户状态或认证版本。`serverTime` 是 UTC ISO 8601 字符串，精度为毫秒。`requestId` 与 `X-Request-ID` 响应头一致，供日志关联；不得将任何秘密放入请求标识。身份与令牌响应使用 `Cache-Control: no-store`。

| 方法与路径 | 输入 | 成功 data |
| --- | --- | --- |
| GET `/api/v1/auth/csrf` | 保存返回的 Cookie | `headerName`、`token` |
| POST `/api/v1/auth/login` | JSON 字符串字段 `account`、`password`，并提交 CSRF 头和 Cookie | `userId`、`account`、`role` |
| GET `/api/v1/users/me` | 登录 Cookie | `userId`、`account`、`role` |
| GET `/api/v1/admin/users/me` | 登录 Cookie，账户角色为 `admin` | `userId`、`account`、`role` |
| POST `/api/v1/auth/logout` | 登录 Cookie 与 CSRF 头 | `null` |

`GET /api/v1/admin/users/me` 只用于验证管理员权限，不提供用户列表、角色修改或禁用操作。游客返回 401，已登录普通用户返回 403，管理员返回自身三个安全字段。

登录只接受上述两个字段。账户去首尾空白、校验 4～32 位 ASCII 字母/数字/下划线，再转小写；密码保留全部字符，按 8～64 个 Unicode 码点校验，不去空白或截断。JSON 可以表达换行、NUL 和补充平面字符。重复字段、额外角色/认证版本字段、尾随 JSON、非字符串字段和解析失败均返回输入错误。账户或密码不符合上述规则时返回 400；规则合法但账户不存在或密码错误时返回 401，两种凭据失败使用相同错误码与提示。已登录时再次登录返回冲突，切换账户须先退出。

| HTTP | code | 含义 |
| --- | --- | --- |
| 200 | 0 | 成功 |
| 400 | 40000 | 无效输入、参数或 JSON |
| 401 | 40100 | 未登录或会话已失效 |
| 401 | 40101 | 账户或密码错误 |
| 403 | 40300 | 无权访问 |
| 403 | 40301 | CSRF 令牌缺失或无效 |
| 403 | 40302 | 账户不可登录；仅在密码已验证后返回 |
| 404 | 40400 | 路径不存在 |
| 405 | 40500 | HTTP 方法不受支持 |
| 409 | 40900 | 已登录，须先退出 |
| 415 | 41500 | 不支持的 Content-Type |
| 429 | 42900 | 登录失败过多；同时返回 `Retry-After` 与 `data.retryAfterSeconds` |
| 500 | 50000 | 未知内部错误 |
| 503 | 50300 | 必需依赖暂不可用 |

错误优先级取决于安全过滤器：例如没有有效 CSRF 的 POST 先返回 403，再进行 JSON 解析。错误信息不包含原始请求体、底层 SQL、堆栈或连接信息。

各状态适用场景：

| HTTP | 典型场景 |
| --- | --- |
| 400 | 登录字段缺失、类型错误、JSON 解析失败、账户/密码不符合输入规则 |
| 401 | 无 Cookie、会话过期或已撤销、账户状态/角色/`authEpoch` 已变化、内部接口的会话无效 |
| 403 | 已登录普通用户访问管理接口、CSRF 缺失或无效、账户不可登录、内部服务凭据无效 |
| 404 | 未映射路径、公共入口的 `/internal/**` 与下游 Actuator |
| 405 | 路径存在但方法不支持（如 GET 登录接口） |
| 409 | 已登录时再次登录；其他冲突不使用该状态 |
| 415 | 登录请求的 Content-Type 不是 JSON |
| 429 | 登录失败达到阈值；带 `Retry-After` 与 `data.retryAfterSeconds` |
| 500 | 未知内部错误；日志只记录请求标识和异常类型 |
| 503 | Redis、MySQL 或身份核验服务等必需依赖不可用，或网关没有可用下游；受保护操作未执行 |

## 依赖故障与恢复

所有错误出口共享同一响应契约：MVC 参数/业务/未知异常由用户服务 `WebErrorAdvice` 处理；Security 认证、授权与 CSRF 拒绝由安全处理器写出；逃出过滤链的共享存储和网关转发故障由 `ContractErrorFilter` 写出；网关自身的路由、负载均衡和代理异常由 `GatewayErrorAdvice` 写出。任何出口都保留真实 HTTP 状态，`code` 与 `requestId` 可关联，且不返回异常文本。

- **Redis 不可用**：读取或保存共享会话失败时，用户服务和网关均返回 `503/50300`；登录不会建立可用身份，受保护业务不会执行。获取 CSRF 需要保存会话，因此同样返回 503，且不向客户端下发可用于后续认证的会话 Cookie。限频所需的 Redis 操作失败同样在认证完成前终止请求。
- **MySQL 不可用**：账户、角色、认证版本和撤销记录的权威读取失败时返回 `503/50300`，不会回退到仅信任 Session 快照或客户端身份头；登录不会因无法写入审计或无法读取账户而成功。
- **网关身份核验不可用**：无可用用户实例、连接失败、超时或非预期响应均在代理前返回 `503/50300`。只有用户服务以 401/403 明确拒绝时，网关才分别返回 401/403；它不会把任何故障当作已认证。
- **超时边界**：网关内部身份核验的 `byte-judge.gateway.identity.connect-timeout`（默认 1s）与 `read-timeout`（默认 2s）可通过 `BYTE_JUDGE_IDENTITY_CONNECT_TIMEOUT` / `BYTE_JUDGE_IDENTITY_READ_TIMEOUT` 覆盖；超时后请求被拒绝，网关日志记录 `identity verification failed requestId=... type=...`。Redis 的 `spring.data.redis.connect-timeout` / `timeout` 与数据库驱动的连接/读取超时由各环境显式配置（local 见 `application-local.yml`，测试使用 1～3 秒），不得依赖无限等待。
- **下游错误**：没有实例时负载均衡给出 503；实例存在但连接被拒绝或确认超时同样映射为 `503/50300`。对经路由代理的业务下游，网关按其已生成的响应（包括错误体）原状态转发，不包装为 200；身份核验是代理前的内部调用，不属于被转发的响应，其非预期结果按上一条返回 503。
- **恢复**：依赖恢复后先用同一浏览器重新获取 CSRF，再完成登录、当前身份和管理权限调用。已退出或已撤销的 Cookie 不会因恢复而复活；其撤销事实在用户库中持久存在，恢复只影响新请求的可执行性。暂停的 Redis 保留原有会话数据，未撤销的会话可继续使用；数据库恢复后权威核验随即重新生效。

故障响应示例（HTTP 503）：

```json
{"code":50300,"data":null,"message":"Service temporarily unavailable","requestId":"6c0f4f5f-...","serverTime":"2026-11-06T02:00:00.000Z"}
```

未知内部错误（HTTP 500）只改变 `code`、`message` 与状态，结构与上例一致，不含堆栈、SQL、内部地址或凭据。

手工演练可在隔离环境暂停依赖，不要删除本地卷或复用生产凭据：`docker compose --env-file .env.local pause redis`（或 `mysql`）观察上述状态，再用 `unpause` 恢复；停止唯一用户实例可观察网关 503，重启并等待注册后恢复。自动化使用独立 Testcontainers 容器，仅暂停、不删除数据。

## 登录失败限频

仅 `POST /api/v1/auth/login` 受此限制；CSRF、身份查询、退出及公开资源不参与登录失败计数，也不会因账户或来源被限制而拒绝。

- 两个维度独立计数：规范化账户（去首尾空白后转小写，与认证使用同一规则）和可信来源地址。每次凭据失败同时计入两个维度；任一维度在 15 分钟固定窗口内累计 10 次失败后，该维度被限制 15 分钟。账户不存在与密码错误走同一路径，不产生可区分的提示。
- 阈值语义：第 10 次失败仍返回 `401/40101`，同时启用限制；入口检查已看到限制的请求在查询账户或校验密码之前返回 `429/42900`。已经通过入口检查的并发请求可能仍完成凭据校验，其中超过阈值的失败由原子计数返回 `429/42900`；不会额外返回凭据失败 401。
- 429 响应头 `Retry-After` 为剩余秒数（向上取整），响应体 `data.retryAfterSeconds` 与之一致；`data` 只含这一个字段，不包含账户是否存在、命中的维度或账户状态。等待期满后可重新尝试。
- 窗口从某维度第一次失败起固定计算，后续失败不延长窗口；限制由 `SET NX` 创建，重复受限请求和并发失败都不延长，也不清除另一维度。成功登录不重置计数。限制到期时对应计数一并过期，下一次从零开始，避免到期后立即再次触发限制。
- Redis 键只包含维度（`account`/`source`）、规范化账户或 IP、固定后缀 `:failures`/`:blocked` 及可配置命名空间，不包含密码、摘要、会话或 CSRF。默认命名空间为 `byte-judge:auth:login`。
- 来源地址由网关 socket 对端确定：网关在转发前剥离客户端 `Forwarded`、`X-Forwarded-*` 与 `X-Internal-*`，再写入唯一的内部头 `X-Internal-Client-Address`。用户服务只有在直接对端属于 `byte-judge.web.trusted-proxies` 时才读取该头，且要求恰好一个合法 IP 字面量；否则使用 socket 对端。因此客户端提供的转发头或内部头不能选择计数来源；直连用户服务且不在可信代理列表中的客户端同样不能。生产必须把该列表配置为网关地址，本地默认值 `127.0.0.1,0:0:0:0:0:0:0:1` 只用于回环调试。
- Redis 不可用时，限频检查失败向上抛出并映射为 503，认证不会在跳过限频的情况下完成；HTTP 故障响应与恢复见[依赖故障与恢复](#依赖故障与恢复)。
- 默认策略为 `byte-judge.auth.rate-limit.window=15m`、`max-failures=10`、`block-duration=15m`。测试使用独立命名空间和短限制验证时间边界，另行断言生产默认值，不使用长时间 sleep。

## Cookie 与令牌生命周期

网关和用户服务使用 Redis indexed Session repository，命名空间 `byte-judge:session`，默认闲置时限 30 分钟，`BJSESSION` Cookie 为 `Path=/; HttpOnly; SameSite=Lax`。生产默认 `Secure=true`；`local` 配置显式覆盖为 false，仅用于回环 HTTP 调试。网关没有业务数据库凭据。

Session 使用 Spring Security 内置认证类型，身份主体是 common-core 中不依赖框架的最小契约（用户编号、规范化账户、角色和 `authEpoch`），服务私有类不进入共享序列化数据。两个进程采用相同的 Spring Security / Session 依赖版本和 JDK 序列化器。登录成功轮换 Session ID；登录前 Cookie 不能获得登录后身份。退出撤销当前会话并清除 Cookie，其他独立会话保持有效；未登录退出返回 401。Redis 中的会话值只是缓存，逐会话撤销事实和账户认证版本以用户库为准，详见下一节。

用户服务负责这些接口的 CSRF 校验及登录/退出修改；网关转发原始 Cookie、CSRF 头和下游 Set-Cookie，但不读取或写入会话（避免在所有者轮换或撤销后回写残片）。所有服务都只信任 Cookie 中的会话，不读取任何请求头身份；网关在转发前剥离客户端传入的 `X-User-*`、`X-Internal-*` 与 `Forwarded` / `X-Forwarded-*` 头，业务服务永远收不到可伪造的身份头（04 §4.2）。`GET /auth/csrf` 返回 `headerName=X-CSRF-TOKEN` 与应当发送的令牌值。令牌在登录和退出时清除；每次成功登录或退出后，重新 GET 获取令牌，再进行后续 POST。返回令牌可能带有随机掩码，不应通过字符串相等判断底层令牌是否变化。默认不开放任意来源 CORS。为避免 Cookie 值与 Session 标识不一致，共享 Cookie 序列化显式关闭 Base64 包装。

## 实时账户状态与管理员权限

登录时 Session 只保存最小身份快照：用户编号、规范化账户、角色和 `authEpoch`。对 `/api/v1/users/me`、`/api/v1/admin/**` 及 `/api/v1/me/**` 的每次受保护请求都会重新读取用户服务权威数据库中的当前角色、账户状态和 `authEpoch`；不使用延迟失效的正向缓存。账户被禁用、`authEpoch` 改变、角色或账户记录不再匹配时，当前会话立即失效并返回 401，恢复账户状态也不能复活旧会话；重新登录会使用新的权威值。权威存储无法完成核验时返回 503，受保护业务不会执行。

网关在入口对上述路径执行一次独立核验：读取 `BJSESSION` 对应 Session，调用用户服务的内部身份接口；`/api/v1/admin/**` 还要求权威角色为 `admin`。题目服务对自己持有的受保护路径（如 `/api/v1/admin/problems/**`）也用同一内部接口和独立凭据执行核验。用户服务在真正处理请求时再次核验，因此直连用户服务不能绕过 Session、CSRF、角色或状态检查。任何内部调用与实际浏览器身份无关，浏览器管理员 Session 不能授权内部接口。

内部身份接口不属于公开 API，网关没有对应路由，公共入口遇到 `/internal/**` 直接返回 404：

| 方法与路径 | 输入 | 成功 data |
| --- | --- | --- |
| POST `/internal/v1/identity/verify` | 请求头 `X-Internal-Service`（`gateway` 或 `problem`）、`X-Internal-Credential`；JSON 字段 `sessionId`、`path` | `userId`、`account`、`role` |

内部凭据按调用方分别配置：

| 调用方 | `X-Internal-Service` | 受控环境变量 | 持有服务 |
| --- | --- | --- | --- |
| 网关 | `gateway` | `BYTE_JUDGE_INTERNAL_GATEWAY_CREDENTIAL` | 网关、用户服务 |
| 题目服务 | `problem` | `BYTE_JUDGE_INTERNAL_PROBLEM_CREDENTIAL` | 题目服务、用户服务 |

两项凭据均至少 32 字符、无默认值，只从环境变量或受控文件注入；`BYTE_JUDGE_INTERNAL_PROBLEM_CREDENTIAL` 在用户服务与题目服务中必须取同一值，部署时还须与网关凭据取不同值（实现只按 `X-Internal-Service` 选择对应凭据，不校验两项是否相同）。用户服务同时校验两项配置，任一项缺失或不足 32 字符时拒绝启动；题目服务在自身凭据缺失或不足 32 字符时同样拒绝启动。接口使用常量时间比较，只接受独立服务身份，不读取浏览器 Cookie 或身份头；成功响应仅包含上述三个白名单字段。无效凭据返回 403，未知或失效 Session 返回 401，权威角色不足管理路径返回 403。`local` 由 `.env.local` 生成本地随机值，其他环境必须由部署方注入。

轮换需要同时更新上述两个持有服务的注入值并重启相应进程；当前实现每个调用方只接受一个当前值，凭据在启动时读取并绑定，不支持热更新、新旧值并行或多密钥兼容（04 §4.3 中“轮换期接受短期过渡版本”仍是设计目标，尚未实现）。轮换题目服务凭据时，在用户服务与题目服务都完成重启前，题目服务的受保护请求会因凭据不匹配返回 403，应安排在维护窗口或接受这段短暂拒绝；网关凭据同理，需同时更新网关与用户服务。

用户服务持久保存认证/权限审计：登录成功与失败（含原因分类）、退出、保护请求的身份核验拒绝、管理权限拒绝和内部凭据拒绝。审计不记录密码、摘要、Session/CSRF 值或服务凭据，也不提供查询页面。`auth_audit` 使用分配式雪花主键（`BYTE_JUDGE_AUDIT_WORKER_ID` / `BYTE_JUDGE_AUDIT_DATACENTER_ID`，多实例必须唯一）、驼峰列名和 UTC `DATETIME(3)`。

受保护路径按 Spring 解码后的路由判断，`%61dmin`、`users/%6de` 等编码别名同样触发实时核验；网关与用户服务使用同一判断。

## 会话撤销、闲置与跨实例

Redis 继续承载共享 Session，但不作为“未撤销”的依据。用户服务在所属数据库的 `revoked_session` 表持久保存逐会话撤销事实：

| 列 | 语义 |
| --- | --- |
| `id` | 分配式雪花主键；多实例必须使用唯一的 `BYTE_JUDGE_AUDIT_WORKER_ID` / `BYTE_JUDGE_AUDIT_DATACENTER_ID` 组合 |
| `sessionIdHash` | Session ID 的 SHA-256，`BINARY(32)` 且唯一；不保存原始 Cookie 值 |
| `revokedAt` | UTC `DATETIME(3)`，默认当前数据库时间 |

- **退出**：先提交撤销记录，再清除 CSRF、失效 Redis Session 并让客户端 Cookie 过期。撤销写入失败（如数据库不可用）时退出返回 503，不会假装成功；其他独立会话不受影响，因为退出不递增账户级 `authEpoch`。同一会话的重复或并发撤销是幂等的：写入命中唯一摘要时才视为已撤销；若写入失败且该摘要不存在（例如雪花主键与另一会话冲突），错误向上传播为 503，而不是静默丢弃撤销事实。
- **核验**：网关内部身份接口和用户服务直连的受保护请求都先检查撤销记录，再读取 Redis 会话；直连不能绕过。账户不存在、账户/角色/状态不匹配或 `authEpoch` 改变时，用户服务先持久撤销该 Session ID，再返回 401。恢复账户状态不会恢复旧会话，只有重新登录会建立新身份。
- **并发保存**：撤销生效点是撤销记录提交，不是 Redis 删除。已经通过核验的在途请求可能完成自身业务；它在撤销后写回 Redis 时可能留下缺少 `creationTime` 等必需字段的残片，共享 Session 适配器把这种哈希当作无效会话（401），而不是 500。即使完整旧 Session 被另一个节点或未完成的删除重新写回，撤销记录仍会让下一次受保护请求返回 401。
- **闲置**：生产默认 30 分钟（`spring.session.timeout=30m`），每次活动访问续期，停止活动后过期。自动化验收用独立短超时观察边界，同时断言运行实例实际使用生产默认值 1800 秒。
- **保留策略**：撤销记录永久保留，不设 TTL 或自动清理。30 分钟闲置时限约束的是“多久不活动后失效”，不能证明撤销前已加载会话的在途请求到何时不会再保存，因此不能据此删除撤销事实。后续若要回收，必须先建立可证明的最大请求生命周期或绝对会话寿命，并让保留窗口超过该上界。
- **跨实例**：所有用户实例复用同一制品，共享 MySQL、Redis、Session 命名空间、`BJSESSION` Cookie 和 JDK 序列化契约；仅端口、日志目录及雪花实例标识不同。网关不做粘性路由，也不在本地缓存正向身份。

会话撤销的自动化验收通过两个同时存活的用户服务进程和真实网关完成；撤销竞争使用仅在测试 classpath 存在的协调过滤器，让一个已通过认证的请求在撤销完成后真正保存响应，再重放旧 Cookie。该过滤器不进入发布制品，也不新增生产接口。

手工复现跨实例与独立会话（同一账户在两个浏览器会话中分别登录）：

```powershell
# 两个用户实例：相同制品、数据库和 Redis，不同端口/管理端口/日志目录/雪花标识
$env:SERVER_PORT = 8081; $env:MANAGEMENT_PORT = 9081
$env:BYTE_JUDGE_AUDIT_WORKER_ID = 0
java -jar byte-judge-models/byte-judge-user-model/target/byte-judge-user-model-0.0.1-SNAPSHOT.jar
# 第二个终端
$env:SERVER_PORT = 8091; $env:MANAGEMENT_PORT = 9091
$env:BYTE_JUDGE_AUDIT_WORKER_ID = 1
java -jar byte-judge-models/byte-judge-user-model/target/byte-judge-user-model-0.0.1-SNAPSHOT.jar
# 第三个终端：网关
java -jar byte-judge-gateway/target/byte-judge-gateway-0.0.1-SNAPSHOT.jar
```

按上文 PowerShell 示例登录并保存 Cookie，重复调用 `GET /api/v1/users/me`；在两个用户实例的 `log/<实例>/access` 中按 `X-Request-ID` 能找到同一会话的不同实例访问记录，身份字段一致。退出后保存旧 Cookie 并重放到网关及两个实例端口（`http://127.0.0.1:8081`、`http://127.0.0.1:8091`），均应返回 401；另一独立会话仍为 200。并发撤销无法用人工点击稳定复现，以自动测试为准。

## PowerShell 调用

先按后端 README 启动基础设施与服务；以下命令不把密码放进命令行参数或历史。交互读取的密码仅在当前进程构造请求期间转为字符串，不保存到文件，不输出请求体。示例适用于 Windows PowerShell 5.1 与 PowerShell 7。

```powershell
$base = 'http://127.0.0.1:8080/api/v1'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
function Get-Csrf {
    (Invoke-RestMethod "$base/auth/csrf" -WebSession $session).data
}
$csrf = Get-Csrf
$account = Read-Host 'Account'
$secret = Read-Host 'Password' -AsSecureString
try {
    $plain = (New-Object System.Net.NetworkCredential('', $secret)).Password
    $body = @{ account = $account; password = $plain } | ConvertTo-Json -Compress
    $headers = @{ $csrf.headerName = $csrf.token }
    $login = Invoke-RestMethod "$base/auth/login" -Method Post -WebSession $session `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([Text.Encoding]::UTF8.GetBytes($body))
    $login
} finally {
    $plain = $null
    $body = $null
    $secret = $null
}
$csrf = Get-Csrf
Invoke-RestMethod "$base/users/me" -WebSession $session
# 管理员账户还应成功：
Invoke-RestMethod "$base/admin/users/me" -WebSession $session
# 普通用户访问同一地址返回 HTTP 403；游客返回 401。
$headers = @{ $csrf.headerName = $csrf.token }
Invoke-RestMethod "$base/auth/logout" -Method Post -WebSession $session -Headers $headers
$csrf = Get-Csrf
# 此时 /users/me 应返回 HTTP 401；PowerShell 会抛出 HTTP 异常。
Invoke-RestMethod "$base/users/me" -WebSession $session
```

接口测试工具也应开启 Cookie 管理，先 GET 令牌，保存 Cookie，再将令牌放入响应指明的请求头。不要手工复制 Cookie/令牌到共享文件、截图或日志。验证错误凭据时应使用一个新的游客会话；已登录会话会先得到 409。

在独立测试环境、来源尚无失败计数时，可用下面的游客会话复现阈值。随机账户无需预先创建，示例不使用真实账户密码，只输出尝试序号、HTTP 状态与等待秒数。默认策略下前 10 次为 401，第 11 次为 429；同一来源已有失败计数时可能提前受限。

```powershell
$base = 'http://127.0.0.1:8080/api/v1'
$guest = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = (Invoke-RestMethod "$base/auth/csrf" -WebSession $guest).data
$headers = @{ $csrf.headerName = $csrf.token }
$body = @{ account = 'limit_' + [guid]::NewGuid().ToString('N').Substring(0, 12)
           password = [guid]::NewGuid().ToString('N') } | ConvertTo-Json -Compress
try {
    1..11 | ForEach-Object {
        $attempt = $_
        try {
            $response = Invoke-WebRequest "$base/auth/login" -UseBasicParsing -Method Post `
                -WebSession $guest -Headers $headers -ContentType 'application/json; charset=utf-8' `
                -Body ([Text.Encoding]::UTF8.GetBytes($body)) -ErrorAction Stop
            [pscustomobject]@{ Attempt = $attempt; Status = [int]$response.StatusCode; RetryAfter = $null }
        } catch {
            if ($null -eq $_.Exception.Response) { throw }
            $response = $_.Exception.Response
            $retry = $null
            if ($response.Headers -is [Net.WebHeaderCollection]) {
                $retry = $response.Headers['Retry-After'] # Windows PowerShell 5.1
            } elseif ($null -ne $response.Headers.RetryAfter) {
                $retry = $response.Headers.RetryAfter.ToString() # PowerShell 7
            }
            [pscustomobject]@{ Attempt = $attempt; Status = [int]$response.StatusCode; RetryAfter = $retry }
        }
    }
} finally {
    $body = $null
}
# 受限后仍可获取公开 CSRF 令牌；生产默认等待 15 分钟后可重新登录。
(Invoke-WebRequest "$base/auth/csrf" -WebSession $guest -UseBasicParsing).StatusCode
```

## 自动验证

主要验收入口是网关真实 HTTP，测试启动真实、独立的 MySQL、Redis 和 Nacos，并通过初始化命令准备管理员。普通用户、禁用账户及需要动态改变状态/认证版本的账户只由测试夹具准备。测试不读取 `.env.local`，不修改已有本地账户。Docker 不可用时失败，不静默跳过。

认证 HTTP 测试覆盖：管理员/普通用户/游客的角色矩阵、管理身份字段白名单、伪造 `X-User-*`/`X-Internal-*`/转发头、登录后账户禁用与 `authEpoch` 递增在下一请求被拒、恢复状态不复活旧会话、重新登录使用新版本、直连用户服务的 Session/CSRF/角色、禁用状态与认证版本检查、角色变更对网关和直连会话立即生效、百分号编码的受保护路径别名不能跳过核验、内部接口独立服务身份（含浏览器 Session 不能授权内部接口）、公共网关不暴露 `/internal/**` 与 Actuator，以及审计事件不含任何凭据材料。网关过滤器另有失败关闭单元测试：核验不可用时返回 503 且不执行下游受保护工作。

会话契约与跨实例测试（`CrossInstanceSessionTest`、`SessionContractTest`）覆盖：两个同时存活的用户服务进程经同一 Cookie 服务受保护请求（用各自 access log 的请求标识证明分流）；运行实例实际应用 1800 秒默认闲置；短超时下活动续期与停止活动后的过期；退出只撤销当前会话、其他独立会话仍有效，旧 Cookie 经网关和两个实例直连均为 401；撤销记录以 32 字节摘要持久存在；协调请求在退出完成后保存旧会话，随后旧 Cookie 仍被拒绝；完整旧 Session 被重新写回 Redis 后仍被拒绝（移除撤销判定时该用例会以 200 失败）；只含属性字段、缺少必需字段的残片哈希经网关和两个实例直连均返回 401 而非 500（该场景没有撤销记录，单独验证残片处理）；禁用账户/递增 `authEpoch` 后下一次请求被拒、恢复状态或回退认证版本后旧会话均不复活、重新登录成功；用户实例重启后撤销仍有效。`MybatisSessionRevocationTest` 另以单元测试锁定：同会话重复撤销幂等成功，雪花主键冲突（摘要不存在）时异常向上传播，且只写入 32 字节摘要。

登录失败限频的真实 HTTP 测试使用不同的环回来源地址和短限制配置（`block-duration=30s`），覆盖：来源维度跨多个账户累计及 429 契约、账户维度跨多个来源并验证规范化、20 个并发失败恰好 10 个通过阈值、伪造 `Forwarded`/`X-Forwarded-For`/`X-Real-IP`/`X-Internal-Client-Address` 不能改变来源、成功登录不重置来源计数、限制到期后正确登录恢复且重复 429 不延长 TTL（到期通过缩短测试键 TTL 模拟，不依赖长时间等待）、直连用户服务的非可信对端忽略内部头，以及 Redis 键不含凭据材料。生产默认 15m/10/15m 由独立配置绑定测试断言；限频不可用时的认证失败关闭由应用层单元测试覆盖，依赖不可用时的 HTTP 503 契约见[依赖故障与恢复](#依赖故障与恢复)。

依赖故障验收由 `DependencyFailureFlowTest` 覆盖（5 项）：暂停 Redis 后，经网关与直连的受保护请求、登录和 CSRF 获取均返回 503 契约且不下发可用 Cookie；恢复后未撤销会话继续返回 200，已退出会话仍为 401（恢复后的重新获取 CSRF 与登录由认证流程测试单独覆盖）。暂停 MySQL 后，网关和直连的权威核验返回 503，恢复后重新返回 200。用户服务内部核验被人为延迟 15 秒时，网关在配置的读取超时内返回 503，并在应用日志留下可关联的失败类型；恢复后同一 Cookie 返回 200。无下游实例与指向已关闭端口的实例分别产生 503 契约。错误出口矩阵逐一断言 400/401/403/404/405/409/415/429/500/503 的状态、数值码、五个字段、`requestId` 关联、`serverTime` 格式以及响应与日志不含敏感材料。故障注入仅存在于测试 classpath 的 `auth-http-test` profile，发布制品没有对应开关。

执行完整测试与构建：在后端根目录选择 Java 21 后运行 `./mvnw.cmd clean package`。只执行认证测试时，先构建网关及其测试运行类路径，再运行用户服务测试：

```powershell
./mvnw.cmd -pl :byte-judge-gateway -am test-compile -DskipTests
./mvnw.cmd -pl :byte-judge-user-model -am test -Dtest=AuthenticationFlowTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw.cmd -pl :byte-judge-user-model -am test -Dtest=CrossInstanceSessionTest,SessionContractTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw.cmd -pl :byte-judge-user-model -am test -Dtest=DependencyFailureFlowTest -Dsurefire.failIfNoSpecifiedTests=false
```

实际通过项、构建输出和未验证边界记录在后端 README；本文的协议不代替运行证据。
