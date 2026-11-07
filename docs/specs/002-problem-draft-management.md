# 规格 002:题目草稿管理(题库首切片)

| 属性 | 值 |
| --- | --- |
| 规格版本 / 日期 | V1.0(已确认)/ 2026-11-07 |
| 对应需求 | FR-11(部分:草稿生命周期)、FR-12(部分:测试数据录入与存储)、BR-05(字段与资源边界) |
| 明确不做 | 版本创建、发布/下架/删除、发布校验执行、C 端检索与详情、判题接入 |
| 状态 | 已实现草稿与测试数据登记切片；父任务 #15，子任务 #16～#19；交付 [PR #20](https://github.com/jack-oliver-123/byte-judge/pull/20) 待审；218 项全量测试及六服务冒烟通过，见[实测记录](../../byte-judge-backend/README.md) |

## Problem Statement

题库是练习与竞赛闭环的第一块业务,但 problem 服务目前只有启动类:无迁移、无表、无接口。架构文档 04 §7 规定不可变版本由校验执行产生(依赖 submission 服务与判题沙箱),而沙箱原型(P-07)尚未执行——因此完整题目生命周期当前无法闭环。维护者需要先落地**不依赖判题**的草稿管理能力,为后续"校验 → 版本 → 发布"切片备好数据模型与文件底座。

当前阻塞:题目库(bj_problem)无任何持久结构;`flyway.enabled=false`;problem 服务无法认证用户(common-web 依赖限制,见决策 D1);错误码表没有版本冲突类业务码。

## Solution

交付 problem 服务内的**管理员草稿工作台**:创建题目(分配题号)、编辑草稿字段(标题/题面/说明/难度/标签/时限/内存)、CAS 保存、后台列表检索、逻辑删除未发布草稿,以及测试数据文件登记(按内容摘要存受保护卷)。全部接口经网关、要求管理员会话、实时核验账户状态、走统一响应与错误码契约。本切片结束时可创建并维护题目草稿与测试数据,但不能发布题目——发布属于"校验+版本"切片。

## User Stories

1. As a 管理员, I want 创建题目草稿时只填标题, so that 可以先占题号再逐步补全内容。
2. As a 管理员, I want 创建后获得永久唯一题号, so that 后续引用、检索、提交都绑定同一身份。
3. As a 管理员, I want 保存时携带 expectedRevision, so that 并发编辑不会互相静默覆盖。
4. As a 管理员, I want 保存冲突时收到当前版本, so that 能基于最新内容决定覆盖或放弃。
5. As a 管理员, I want 草稿阶段就执行内容上限, so that 不会到发布时才发现题面超限。
6. As a 管理员, I want 按标题包含与难度筛选后台列表, so that 能在题库增长后定位题目。
7. As a 管理员, I want 上传测试数据文件并得到登记回执, so that 文件按摘要寻址、可校验完整性。
8. As a 管理员, I want 删除没有发布历史的草稿, so that 误建的题目不长期占据列表(题号不复用)。
9. As a 平台维护者, I want 每个管理写操作记录审计, so that 题目数据的变更可追溯。
10. As a 普通用户, I want 管理接口拒绝我的请求, so that 草稿与测试数据不外泄。
11. As a 未登录调用者, I want 收到 401 统一结构, so that 能区分未登录与业务错误。

## Implementation Decisions

### D1 业务服务复用 common-web(架构决策,随实现写 ADR)

problem 管理接口需要"会话认证 + 管理员角色 + 实时账户核验"。04 §4.1 要求网关与业务服务共享 Spring Session 并逐请求重校验;05 §3.2 现行条文只允许网关与用户服务依赖 common-web。决定:**修订 05 §3.2** 为"需要共享会话契约的服务(网关、用户、及已启用受保护业务接口的业务服务)显式依赖",本切片让 problem 显式 `@Import` Session/Security 装配,保持"无业务规则、扫描限自身根包"约束。理由:与 04 既定方向一致、改动最小;备选的独立窄模块会增加模块与装配边界。共享会话与每请求远程核验同时使用：会话提供身份快照，用户服务按权威账户与撤销事实核验，题目服务使用独立内部凭据且不访问用户库；依赖失败拒绝处理，不使用正向缓存。取舍记录见 [ADR-0001](../adr/0001-business-service-shared-session.md)。

### D2 数据与规范

- 库 `bj_problem`(即现有 `bj_problem`,模板/配置/文档已一致,不改名);启用 Flyway,首批迁移建表如下,列名驼峰、雪花分配 `BIGINT` 主键、无 AUTO_INCREMENT,对外 ID 十进制字符串,时间 UTC `DATETIME(3)`。
- `Problem`:id、problemNo(唯一、不复用、逻辑删除后同样占用)、currentDraftId、status(DRAFT/DELETED)、everPublished(false,本切片恒 false)、audit 字段。
- `ProblemDraft`:id、problemId、revision(从 0 递增,CAS 依据)、title、statement、inputDescription、outputDescription、difficulty(NULLABLE/简单/中等/困难)、tags(JSON 数组,≤5)、timeLimitCpuMs、timeLimitWallMs、memoryLimitMiB、isDelete、audit 字段。题面+说明合计 UTF-8 ≤100 KiB。
- `ProblemArtifact`:id、problemId、draftId、contentDigest(SHA-256)、byteSize、fileName、kind(样例输入/样例输出/隐藏输入/隐藏输出)、caseOrder、displayOrder、isDelete、audit 字段。文件本体按摘要存受保护卷(路径仅服务内部解析,不进入任何 API 响应)。
- `problemAudit`:沿用用户服务审计表结构模式,记录操作人/动作/对象/时间/requestId。

### D3 草稿字段语义(冻结)

- **创建**:仅要求 title 为 1~100 个去首尾空白后非空白的 Unicode 码点;其余字段为空;创建即分配题号与 revision=0。title 保存去首尾空白后的值。
- **保存**:PATCH 语义携带完整可编辑字段集 + expectedRevision；可空字段也必须显式传 null，拒绝未知字段、重复键、尾随内容与标量强转;服务端 CAS:当前 revision == expectedRevision 才接受,接受后 revision+1(含无实质变更的保存)。冲突返回 409 + `VERSION_CONFLICT` + 当前草稿内容与 revision。
- **草稿阶段即执行的上限**(与 FR-11/BR-05 一致,不等发布):题面+说明合计 ≤100 KiB;标签 ≤5 个、每个 1~20 非空白码点;CPU 100~10,000 ms、墙钟 100~30,000 ms 且 ≥CPU、内存 64~2,048 MiB;三者可整体为空,出现即校验。
- **题号**:独立计数器(单调递增行 + 事务内 `SELECT ... FOR UPDATE`),从 1000 起,不因逻辑删除复用。
- **逻辑删除**:仅允许删除 status=DRAFT(无任何发布历史)的题目;DELETED 后 problemNo 永久占用;后台列表默认排除 DELETED,可显式查询。

### D4 接口契约

路由沿用 04 §12.2 骨架,全部相对 `/api/v1`,经网关;管理组 `/admin/problems/**`:

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/admin/problems` | 创建草稿,仅 title;返回题号、草稿 id、revision |
| GET | `/admin/problems` | 后台列表:title 包含(AND)、难度(AND)、`pageNum≥1`/`pageSize∈{10,20,50}` 默认 20;创建时间倒序 |
| GET | `/admin/problems/{id}` | 草稿详情全字段(管理员视图,含隐藏数据清单元信息,不含文件内容) |
| PATCH | `/admin/problems/{id}/draft` | CAS 保存可编辑字段 + expectedRevision |
| DELETE | `/admin/problems/{id}` | 逻辑删除 DRAFT 题目;携带 expectedRevision |
| POST | `/admin/problems/{id}/draft/artifacts` | 登记测试数据文件(multipart);返回 artifactId、contentDigest、byteSize |

- 响应统一 `{code,data,message,requestId,serverTime}`,`code=0` 成功;身份与详情响应 `Cache-Control: no-store`。
- 所有写操作要求 CSRF 头;修改类命令的"必要理由"字段本切片不引入(无发布/下架语义),留给版本切片。

### D5 错误码扩充(在 common-core `ApiError` 冻结)

新增:`VERSION_CONFLICT`(40900 与既有 ALREADY_AUTHENTICATED 冲突,故取 **40910**/HTTP 409)、`PROBLEM_NOT_FOUND` 沿用既有 `NOT_FOUND`(40400)、文件校验失败用 `INVALID_REQUEST`(40000)。数值与既有 13 个码不重叠;04 §12.1 的 `NOT_LOGIN` 示例名不引入,沿用已冻结的 `NOT_AUTHENTICATED`。

### D6 认证与安全

- 网关:既有 `/api/v1/admin/**` 路由(problem 已在网关路由表)携带会话 Cookie 转发;problem 服务侧 Security 过滤链对 `/api/v1/admin/**` 要求 `ROLE_admin` + 实时核验(IdentityRevalidationFilter 复用,ProtectedPaths 已覆盖 `/api/v1/admin/` 前缀,无需扩展)。
- 直连 problem 服务(绕过网关)同样执行认证授权——服务自身过滤链完整,不信任任何身份头。
- 雪花节点标识:problem 服务新增 `byte-judge.problem.worker-id/datacenter-id`(无默认值,缺失启动失败),部署约定同用户服务(并发实例组合唯一)。
- 文件上传:仅管理员;multipart 大小限制 1 MiB/文件；按 FR-12 允许零字节输入/期望输出，文件 part 必须存在；样例与隐藏数据合计最多 20 组，草稿可只登记某组的一侧;存储按内容摘要寻址,先写临时文件、核验摘要、原子改名,再事务提交 manifest 引用;事务失败的无引用对象延迟清理(本切片实现清理定时任务的可选性留给实现评审,不作为验收项)。

## Testing Decisions

- 行为测试边界沿用 001:从网关发起真实 HTTP,联通真实 problem 服务、MySQL、Redis、Nacos。测试夹具直接调用受控管理员创建命令准备账号,不新增公开注册。
- 断言外部行为:HTTP 状态与业务码、字段白名单、revision CAS 行为、题号唯一性、文件摘要登记回执;不以 Mapper 调用次数或私有方法为验收。
- 场景:创建(仅标题/非法标题);保存(正常 CAS/过期 revision 冲突含当前版本回显/无变更保存仍递增);上限(题面 100 KiB 边界、标签 5+1、时限内存边界值);列表(标题包含、难度筛选、分页三档、DELETED 不出现);逻辑删除(题号不复用、重复删除幂等语义明确);文件(≤1 MiB 上限、摘要回执、同内容重复登记的行为在实现评审时冻结);权限(游客 401、普通用户 403、伪造头无效、直连服务仍鉴权)。
- 审计:管理写操作产生审计记录;日志不含文件内容与题面全文。
- Java 21 完整构建 + 受影响底座冒烟;README 记录实测结果与未验证项(发布、校验、检索性能不在本切片)。

## Out of Scope

- 版本创建、发布/下架/删除语义、受理许可协议(§5.2)接入、ValidationSpec/ValidationExecution。
- C 端检索与详情(FR-03/04)、ProblemAccess 上下文矩阵、公开投影。
- 判题沙箱、go-judge、提交服务任何改动。
- 普通用户注册、资料、消息、竞赛、调度。

## Further Notes

- 05 §3.2 的修订与 ADR-0001 随本切片实现提交;005 文档版本头(V1.4 vs V1.5 记录不一致)顺带修正。
- `user_account` 的 snake_case+AUTO_INCREMENT 与 04 §11.1 的偏差记录为遗留,不在本切片修正。
- 实现评审冻结：同一草稿的 `kind + caseOrder` 槽位重复登记返回 400，不替换；同摘要可由其他槽位或题目独立引用。登记要求 expectedRevision，成功递增草稿修订。caseOrder 为 1～20，displayOrder 为 0～10000；样例与隐藏组数合计最多 20，达到上限仍可补齐既有组的另一侧。无引用对象与临时残留暂不自动清理，部署方保留受控清理边界。
- DELETE 使用 expectedRevision 查询参数；重复相同已接受修订幂等返回 200，不重复审计，已删除题目携带其他修订返回 404。列表用 includeDeleted=true 显式包含已删除条目，详情不读取已删除题目。
- 需求追踪:FR-11 的发布/下架/删除条款、FR-12 的校验条款均不在本切片验收范围;本切片只主张草稿与数据登记部分。
