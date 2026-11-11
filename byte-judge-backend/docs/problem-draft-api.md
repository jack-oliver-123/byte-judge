# 题目草稿管理接口

本接口对应规格 [002](../../docs/specs/002-problem-draft-management.md) 的草稿与测试数据登记切片。入口为网关 `http://127.0.0.1:8080/api/v1/admin/problems`；创建管理员、登录和获取 CSRF 的步骤见 [认证接口](auth-api.md)。所有接口要求管理员会话，题目服务直连同样校验身份；响应采用 `{code,data,message,requestId,serverTime}`，管理数据返回 `Cache-Control: no-store`。

| 方法 | 路径（相对 `/api/v1/admin/problems`） | 用途 |
| --- | --- | --- |
| POST | 空路径 | 仅填标题创建题目及草稿，分配永久题号，初始 revision 为 0 |
| GET | 空路径 | 标题包含与难度 AND 筛选、分页查询 |
| GET | `/{id}` | 管理员草稿详情及测试数据清单元信息 |
| PATCH | `/{id}/draft` | 携带 expectedRevision 保存完整可编辑字段集 |
| DELETE | `/{id}` | 按 expectedRevision 逻辑删除未发布草稿 |
| POST | `/{id}/draft/artifacts` | multipart 登记输入或期望输出文件 |

`id`、草稿 ID 和测试数据 ID 均为十进制字符串；`problemNo` 是从 1000 起递增的题号。逻辑删除保留题号，后续创建不复用它。该切片不提供发布、版本创建、C 端详情或文件下载接口。

## 草稿字段与并发

创建请求为 JSON，例如 `{"title":"A + B"}`。标题去首尾 Unicode 空白后必须非空，最多 100 个 Unicode 码点。创建时其他内容为空，后续可分次完善；成功 data 为 `{id,draftId,problemNo,revision}`。

创建与保存均拒绝未知字段、重复键、尾随 JSON 和标量类型强转。PATCH 要求完整字段集，可清空的字段也必须显式传 null，防止误把部分请求当作完整保存。

PATCH 携带完整可编辑字段集：`title`、`statement`、`inputDescription`、`outputDescription`、`difficulty`、`tags`、`timeLimitCpuMs`、`timeLimitWallMs`、`memoryLimitMiB`，以及 `expectedRevision`。内容可以不完整，但题面与输入/输出说明合计最多 100 KiB UTF-8；标签最多 5 个，每个 1～20 个非空白码点。难度使用 `EASY` / `MEDIUM` / `HARD`，未定时为空。资源限制可三项整体为空，填写时 CPU 为 100～10000 ms、墙钟为 100～30000 ms 且不小于 CPU、内存为 64～2048 MiB。

每次接受保存都会递增 revision，包括内容未变的保存。过期的 expectedRevision 返回 HTTP 409、`code=40910`，`data` 带当前草稿及其 revision；客户端应展示冲突并让管理员基于当前内容重新编辑。文件清单变化同样参与草稿修订，防止上传与其他管理写操作互相覆盖。

列表使用 `title`、`difficulty`、`pageNum`、`pageSize` 参数；pageNum 从 1 起，pageSize 允许 10 / 20 / 50，默认 20。标题中的 `%`、`_` 按普通字符匹配。默认排除逻辑删除的题目，使用 `includeDeleted=true` 可同时查看已删除条目；按创建时间及题号倒序。data 为 `{items,total,pageNum,pageSize}`，每项包含 `{id,problemNo,title,difficulty,status,revision,createdAt,updatedAt}`。

详情与保存成功的 data 为平铺的 `{id,problemNo,status,everPublished,revision,draftId,createdAt,updatedAt}`、上述全部可编辑字段，以及 `artifacts` 清单数组。已删除题目的详情返回 404。

删除使用 `DELETE /{id}?expectedRevision=N`，成功 data 为 `{id,problemNo,status:"DELETED",revision}`。重复提交同一个已接受的 expectedRevision 返回 200，不重复写删除审计；已删除题目携带其他 revision 返回 404。题号始终保留。

## 测试数据与受保护存储

上传使用 multipart 字段 `file`、`kind`、`caseOrder`、`displayOrder`、`expectedRevision`。kind 使用 `SAMPLE_INPUT` / `SAMPLE_OUTPUT` / `HIDDEN_INPUT` / `HIDDEN_OUTPUT`；同类输入与输出按 caseOrder 组成一组，displayOrder 表示展示顺序。草稿可以先登记一组的一侧，样例与隐藏数据合计最多 20 组。

每文件最多 1 MiB（1048576 字节），允许零字节文件，缺少 file part 无效。回执包含 artifactId、SHA-256 contentDigest 和 byteSize。文件按摘要存放在 `BYTE_JUDGE_ARTIFACT_ROOT` 受保护卷；文件名只作清单元信息，API 不接受或返回存储路径，隐藏数据内容不进入响应或日志。相同内容可被不同题目或数据槽位引用，共用不可变文件对象。同一草稿的 `kind + caseOrder` 槽位只能登记一次，重复登记返回 400；不同槽位可复用同一摘要。caseOrder 为 1～20，displayOrder 为 0～10000。成功登记递增草稿 revision，继续编辑前读取最新详情。

文件在数据库事务外完成有界读取、摘要计算和同卷原子落盘，随后在数据库事务内登记清单、更新草稿 revision 并写审计。失败事务不会留下可见的清单引用；已经落盘但无引用的对象暂时保留，本切片不自动清理。备份和恢复时需共同保留题目库与文件卷。

## 错误与验收

| HTTP / code | 含义 |
| --- | --- |
| 400 / 40000 | 字段、资源限制或文件登记请求无效 |
| 401 / 40100 | 未登录或会话已失效 |
| 403 / 40300 | 当前角色无管理员权限 |
| 403 / 40301 | CSRF 缺失或无效 |
| 404 / 40400 | 题目不存在或不可用于当前操作 |
| 409 / 40910 | 草稿 revision 冲突；保存冲突时带当前详情 |
| 503 / 50300 | 身份权威、会话存储或业务存储无法完成必需操作 |

所有写请求发送当前 CSRF 请求头，登录或退出后重新取得 token。不得以 `X-User-*` 等身份头代替会话；内部核验凭据仅由受控服务进程持有。

完整构建在后端根目录以 Java 21 执行 `./mvnw.cmd -B clean package`。真实 HTTP 验收入口为用户服务测试中的 `ProblemDraftFlowTest`，通过独立 MySQL / Redis / Nacos 和网关、用户、题目进程验证；单独运行前先打包网关与题目服务。实际通过结果、日志和未验证项统一记录在 [后端 README](../README.md)，构建成功不替代业务验收。
