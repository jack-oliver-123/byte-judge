# 登录、当前身份、退出与管理员权限接口

本文对应 #7、#8、#10。调用入口为本地网关 `http://127.0.0.1:8080`；部署使用同源 HTTPS。管理员先按 [初始化说明](admin-initialization.md) 创建，普通用户由受控测试夹具准备，当前没有公开注册接口。会话并发撤销及 Nacos Config 由后续票据验收。

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

## 登录失败限频

仅 `POST /api/v1/auth/login` 受此限制；CSRF、身份查询、退出及公开资源不参与登录失败计数，也不会因账户或来源被限制而拒绝。

- 两个维度独立计数：规范化账户（去首尾空白后转小写，与认证使用同一规则）和可信来源地址。每次凭据失败同时计入两个维度；任一维度在 15 分钟固定窗口内累计 10 次失败后，该维度被限制 15 分钟。账户不存在与密码错误走同一路径，不产生可区分的提示。
- 阈值语义：第 10 次失败仍返回 `401/40101`，同时启用限制；入口检查已看到限制的请求在查询账户或校验密码之前返回 `429/42900`。已经通过入口检查的并发请求可能仍完成凭据校验，其中超过阈值的失败由原子计数返回 `429/42900`；不会额外返回凭据失败 401。
- 429 响应头 `Retry-After` 为剩余秒数（向上取整），响应体 `data.retryAfterSeconds` 与之一致；`data` 只含这一个字段，不包含账户是否存在、命中的维度或账户状态。等待期满后可重新尝试。
- 窗口从某维度第一次失败起固定计算，后续失败不延长窗口；限制由 `SET NX` 创建，重复受限请求和并发失败都不延长，也不清除另一维度。成功登录不重置计数。限制到期时对应计数一并过期，下一次从零开始，避免到期后立即再次触发限制。
- Redis 键只包含维度（`account`/`source`）、规范化账户或 IP、固定后缀 `:failures`/`:blocked` 及可配置命名空间，不包含密码、摘要、会话或 CSRF。默认命名空间为 `byte-judge:auth:login`。
- 来源地址由网关 socket 对端确定：网关在转发前剥离客户端 `Forwarded`、`X-Forwarded-*` 与 `X-Internal-*`，再写入唯一的内部头 `X-Internal-Client-Address`。用户服务只有在直接对端属于 `byte-judge.web.trusted-proxies` 时才读取该头，且要求恰好一个合法 IP 字面量；否则使用 socket 对端。因此客户端提供的转发头或内部头不能选择计数来源；直连用户服务且不在可信代理列表中的客户端同样不能。生产必须把该列表配置为网关地址，本地默认值 `127.0.0.1,0:0:0:0:0:0:0:1` 只用于回环调试。
- Redis 不可用时，限频检查失败向上抛出并映射为 503，认证不会在跳过限频的情况下完成；依赖故障的完整响应由最终集成切片验收。
- 默认策略为 `byte-judge.auth.rate-limit.window=15m`、`max-failures=10`、`block-duration=15m`。测试使用独立命名空间和短限制验证时间边界，另行断言生产默认值，不使用长时间 sleep。

## Cookie 与令牌生命周期

网关和用户服务使用 Redis indexed Session repository，命名空间 `byte-judge:session`，默认闲置时限 30 分钟，`BJSESSION` Cookie 为 `Path=/; HttpOnly; SameSite=Lax`。生产默认 `Secure=true`；`local` 配置显式覆盖为 false，仅用于回环 HTTP 调试。网关没有业务数据库凭据。

Session 使用 Spring Security 内置认证类型，身份主体是 common-core 中不依赖框架的最小契约（用户编号、规范化账户、角色），服务私有类不进入共享序列化数据。两个进程采用相同的 Spring Security / Session 依赖版本和 JDK 序列化器。登录成功轮换 Session ID；登录前 Cookie 不能获得登录后身份。退出删除当前会话并清除 Cookie，其他独立会话保持有效；未登录退出返回 401。并发旧请求保存与撤销竞争的完整保证属于 #9。

用户服务负责这些接口的 CSRF 校验及登录/退出修改；网关转发原始 Cookie、CSRF 头和下游 Set-Cookie，但不读取或写入会话（避免在所有者轮换或撤销后回写残片）。所有服务都只信任 Cookie 中的会话，不读取任何请求头身份；网关在转发前剥离客户端传入的 `X-User-*`、`X-Internal-*` 与 `Forwarded` / `X-Forwarded-*` 头，业务服务永远收不到可伪造的身份头（04 §4.2）。`GET /auth/csrf` 返回 `headerName=X-CSRF-TOKEN` 与应当发送的令牌值。令牌在登录和退出时清除；每次成功登录或退出后，重新 GET 获取令牌，再进行后续 POST。返回令牌可能带有随机掩码，不应通过字符串相等判断底层令牌是否变化。默认不开放任意来源 CORS。为避免 Cookie 值与 Session 标识不一致，共享 Cookie 序列化显式关闭 Base64 包装。

## 实时账户状态与管理员权限

登录时 Session 只保存最小身份快照：用户编号、规范化账户、角色和 `authEpoch`。对 `/api/v1/users/me`、`/api/v1/admin/**` 及 `/api/v1/me/**` 的每次受保护请求都会重新读取用户服务权威数据库中的当前角色、账户状态和 `authEpoch`；不使用延迟失效的正向缓存。账户被禁用、`authEpoch` 改变、角色或账户记录不再匹配时，当前会话立即失效并返回 401，恢复账户状态也不能复活旧会话；重新登录会使用新的权威值。权威存储无法完成核验时返回 503，受保护业务不会执行。

网关在入口对上述路径执行一次独立核验：读取 `BJSESSION` 对应 Session，调用用户服务的内部身份接口；`/api/v1/admin/**` 还要求权威角色为 `admin`。用户服务在真正处理请求时再次核验，因此直连用户服务不能绕过 Session、CSRF、角色或状态检查。网关内部调用与实际浏览器身份无关，浏览器管理员 Session 不能授权内部接口。

内部身份接口不属于公开 API，网关没有对应路由，公共入口遇到 `/internal/**` 直接返回 404：

| 方法与路径 | 输入 | 成功 data |
| --- | --- | --- |
| POST `/internal/v1/identity/verify` | 请求头 `X-Internal-Service: gateway`、`X-Internal-Credential`；JSON 字段 `sessionId`、`path` | `userId`、`account`、`role` |

内部凭据由受控环境变量 `BYTE_JUDGE_INTERNAL_GATEWAY_CREDENTIAL` 提供给网关和用户服务，至少 32 字符，无默认值；缺失时服务拒绝启动。接口使用常量时间比较，只接受独立服务身份，不读取浏览器 Cookie 或身份头；成功响应仅包含上述三个白名单字段。无效凭据返回 403，未知或失效 Session 返回 401，权威角色不足管理路径返回 403。`local` 由 `.env.local` 生成本地随机值，其他环境必须由部署方注入。

用户服务持久保存认证/权限审计：登录成功与失败（含原因分类）、退出、保护请求的身份核验拒绝、管理权限拒绝和内部凭据拒绝。审计不记录密码、摘要、Session/CSRF 值或服务凭据，也不提供查询页面。

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

认证 HTTP 测试覆盖：管理员/普通用户/游客的角色矩阵、管理身份字段白名单、伪造 `X-User-*`/`X-Internal-*`/转发头、登录后账户禁用与 `authEpoch` 递增在下一请求被拒、恢复状态不复活旧会话、重新登录使用新版本、直连用户服务的 Session/CSRF/角色检查、内部接口独立服务身份（含浏览器 Session 不能授权内部接口）、公共网关不暴露 `/internal/**` 与 Actuator，以及审计事件不含任何凭据材料。网关过滤器另有失败关闭单元测试：核验不可用时返回 503 且不执行下游受保护工作。

登录失败限频的真实 HTTP 测试使用不同的环回来源地址和短限制配置（`block-duration=30s`），覆盖：来源维度跨多个账户累计及 429 契约、账户维度跨多个来源并验证规范化、20 个并发失败恰好 10 个通过阈值、伪造 `Forwarded`/`X-Forwarded-For`/`X-Real-IP`/`X-Internal-Client-Address` 不能改变来源、成功登录不重置来源计数、限制到期后正确登录恢复且重复 429 不延长 TTL（到期通过缩短测试键 TTL 模拟，不依赖长时间等待）、直连用户服务的非可信对端忽略内部头，以及 Redis 键不含凭据材料。生产默认 15m/10/15m 由独立配置绑定测试断言；限频不可用时的认证失败关闭由应用层单元测试覆盖，HTTP 503 的完整故障响应留待最终切片。

执行完整测试与构建：在后端根目录选择 Java 21 后运行 `./mvnw.cmd clean package`。只执行认证测试时，先构建网关及其测试运行类路径，再运行用户服务测试：

```powershell
./mvnw.cmd -pl :byte-judge-gateway -am test-compile -DskipTests
./mvnw.cmd -pl :byte-judge-user-model -am test -Dtest=AuthenticationFlowTest -Dsurefire.failIfNoSpecifiedTests=false
```

实际通过项、构建输出和未验证边界记录在后端 README；本文的协议不代替运行证据。
