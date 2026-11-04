# 登录、当前身份与退出接口

本文对应 #7。调用入口为本地网关 `http://127.0.0.1:8080`；部署使用同源 HTTPS。管理员先按 [初始化说明](admin-initialization.md) 创建，普通用户由受控测试夹具准备，当前没有公开注册接口。实时账户状态核验、管理员入口、会话并发撤销、限频及 Nacos Config 由后续票据验收。

## 协议

四个接口均使用 `/api/v1` 前缀，响应为 JSON：

```json
{"code":0,"data":{"userId":"1","account":"operator","role":"admin"},"message":"OK","requestId":"opaque-id","serverTime":"2026-11-04T00:00:00.000Z"}
```

`code` 是整数，仅 `0` 表示成功。失败时 `data=null`；HTTP 状态保持实际错误语义。`userId` 为十进制字符串，`role` 只来自数据库，取 `admin` 或 `user`。响应不包含密码摘要、账户状态或认证版本。`serverTime` 是 UTC ISO 8601 字符串，精度为毫秒。`requestId` 与 `X-Request-ID` 响应头一致，供日志关联；不得将任何秘密放入请求标识。身份与令牌响应使用 `Cache-Control: no-store`。

| 方法与路径 | 输入 | 成功 data |
| --- | --- | --- |
| GET `/api/v1/auth/csrf` | 保存返回的 Cookie | `headerName`、`token` |
| POST `/api/v1/auth/login` | JSON 字符串字段 `account`、`password`，并提交 CSRF 头和 Cookie | `userId`、`account`、`role` |
| GET `/api/v1/users/me` | 登录 Cookie | `userId`、`account`、`role` |
| POST `/api/v1/auth/logout` | 登录 Cookie 与 CSRF 头 | `null` |

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
| 500 | 50000 | 未知内部错误 |
| 503 | 50300 | 必需依赖暂不可用 |

错误优先级取决于安全过滤器：例如没有有效 CSRF 的 POST 先返回 403，再进行 JSON 解析。错误信息不包含原始请求体、底层 SQL、堆栈或连接信息。

## Cookie 与令牌生命周期

网关和用户服务使用 Redis indexed Session repository，命名空间 `byte-judge:session`，默认闲置时限 30 分钟，`BJSESSION` Cookie 为 `Path=/; HttpOnly; SameSite=Lax`。生产默认 `Secure=true`；`local` 配置显式覆盖为 false，仅用于回环 HTTP 调试。网关没有业务数据库凭据。

Session 使用 Spring Security 内置认证类型，身份主体是 common-core 中不依赖框架的最小契约（用户编号、规范化账户、角色），服务私有类不进入共享序列化数据。两个进程采用相同的 Spring Security / Session 依赖版本和 JDK 序列化器。登录成功轮换 Session ID；登录前 Cookie 不能获得登录后身份。退出删除当前会话并清除 Cookie，其他独立会话保持有效；未登录退出返回 401。并发旧请求保存与撤销竞争的完整保证属于 #9。

用户服务负责这四个接口的 CSRF 校验及登录/退出修改；网关转发原始 Cookie、CSRF 头和下游 Set-Cookie，但不读取或写入会话（避免在所有者轮换或撤销后回写残片）。本切片所有服务都只信任 Cookie 中的会话，不读取任何请求头身份；架构文档要求的外部伪造 `X-User-*` / 内部鉴权头剥离随内部鉴权切片实现，当前尚未启用。`GET /auth/csrf` 返回 `headerName=X-CSRF-TOKEN` 与应当发送的令牌值。令牌在登录和退出时清除；每次成功登录或退出后，重新 GET 获取令牌，再进行后续 POST。返回令牌可能带有随机掩码，不应通过字符串相等判断底层令牌是否变化。默认不开放任意来源 CORS。

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
$headers = @{ $csrf.headerName = $csrf.token }
Invoke-RestMethod "$base/auth/logout" -Method Post -WebSession $session -Headers $headers
$csrf = Get-Csrf
# 此时 /users/me 应返回 HTTP 401；PowerShell 会抛出 HTTP 异常。
Invoke-RestMethod "$base/users/me" -WebSession $session
```

接口测试工具也应开启 Cookie 管理，先 GET 令牌，保存 Cookie，再将令牌放入响应指明的请求头。不要手工复制 Cookie/令牌到共享文件、截图或日志。验证错误凭据时应使用一个新的游客会话；已登录会话会先得到 409。

## 自动验证

主要验收入口是网关真实 HTTP，测试启动真实、独立的 MySQL、Redis 和 Nacos，并通过初始化命令准备管理员。普通用户和禁用账户只由测试夹具准备。测试不读取 `.env.local`，不修改已有本地账户。Docker 不可用时失败，不静默跳过。

执行完整测试与构建：在后端根目录选择 Java 21 后运行 `./mvnw.cmd clean package`。只执行认证测试时，先构建网关及其测试运行类路径，再运行用户服务测试：

```powershell
./mvnw.cmd -pl :byte-judge-gateway -am test-compile -DskipTests
./mvnw.cmd -pl :byte-judge-user-model -am test -Dtest=AuthenticationFlowTest -Dsurefire.failIfNoSpecifiedTests=false
```

实际通过项、构建输出和未验证边界记录在后端 README；本文的协议不代替运行证据。
