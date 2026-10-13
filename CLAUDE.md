# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 仓库结构

monorepo，两个子项目各自独立构建，**没有根级构建工具**：

- `byte-judge-backend/` — Spring Boot 后端（端口 8101，context-path `/api`）
- `byte-judge-frontend/` — Vue 3 前端（dev server 端口 8080）

前端的详细约定（路由/权限/请求层/状态管理）见 `byte-judge-frontend/CLAUDE.md`，本文件不重复。

项目处于骨架阶段：后端只保留了用户、文件上传、微信三个模块，前端已搭好布局与权限骨架，**判题业务尚未开始**。

## 常用命令

后端在 `byte-judge-backend/` 下执行（需 JDK 21，Maven 用系统安装的即可，不必走 `mvnw`）：

```bash
mvn compile                              # 编译
mvn test                                 # 跑全部测试（需要本地 MySQL，见下）
mvn -Dtest=UserServiceTest test          # 跑单个测试类
mvn -Dtest=UserServiceTest#userRegister test   # 跑单个测试方法
mvn spring-boot:run                      # 启动，默认 dev profile
mvn package -DskipTests                  # 打包
```

前端在 `byte-judge-frontend/` 下执行：`npm run serve` / `npm run build` / `npm run lint`，细节见其 CLAUDE.md。

## 后端架构

包根 `com.jack.oj`，标准分层：`controller` → `service`/`service.impl` → `mapper`（MyBatis-Plus `BaseMapper`，XML 在 `src/main/resources/mapper/`）→ `model`（`entity` / `dto` / `vo` / `enums`）。

横切的几处：

- `common/BaseResponse` + `ResultUtils` — 统一响应体 `{ code, data, message }`，成功 `code = 0`。
- `exception/GlobalExceptionHandler` — 把 `BusinessException` 转成 `BaseResponse`。**业务失败仍返回 HTTP 200**，未登录是 `code: 40100`、无权限是 `code: 40101`，前端不能靠 HTTP 状态码判断。错误码全集在 `common/ErrorCode`。
- `aop/AuthInterceptor` — `@AuthCheck(mustRole = "admin")` 注解的实现，从 Session 取登录用户校验角色。
- `config/CorsConfig` — `allowCredentials(true)` + `allowedOriginPatterns("*")`，配合前端跨域带 Cookie。
- `config/JsonConfig` — Long 转 String，避免前端 JS 精度丢失。

**鉴权是 Spring Session + Cookie**，不是 JWT。`MainApplication` 上 `@SpringBootApplication(exclude = {RedisAutoConfiguration.class})` 排除了 Redis 自动配置，所以当前 Session 存在内存里；要开分布式 Session 需同时去掉这个 exclude、解开 `application.yml` 里 `spring.session.store-type: redis` 与 `spring.redis` 的注释。

## 后端技术栈与几个易踩的版本点

Spring Boot **3.5.16** / Java **21** / MyBatis-Plus **3.5.17**。以下是从 2.7 升级后与旧模板差异最大的地方，照旧写法会编译或启动失败：

- MyBatis-Plus 3.5.9 起 `IService` / `ServiceImpl` 的包是 `com.baomidou.mybatisplus.spring.service`，**不是** `extension.service`。
- 分页插件 `PaginationInnerInterceptor` 被拆出 starter，靠单独的 `mybatis-plus-jsqlparser` 依赖提供。
- **不要**再引入 `mybatis-spring-boot-starter`：它和 `mybatis-plus-spring-boot3-starter` 冲突，会把 `mybatis-spring` 钉在 2.x，启动时抛 `Invalid value type for attribute 'factoryBeanObjectType'`。
- 依赖坐标已改名：`com.mysql:mysql-connector-j`（不再是 `mysql:mysql-connector-java`）、`knife4j-openapi3-jakarta-spring-boot-starter`。
- 新增依赖时注意 `jakarta.*` 命名空间，`javax.servlet` / `javax.annotation` 已不在依赖树里。

`README.md` 仍是升级前的模板原文（写着 Spring Boot 2.7、帖子与 ES 功能），**以代码和本文件为准**。

## 已删除的模板模块

鱼皮初始模板自带的**帖子 / 点赞 / 收藏 / Elasticsearch** 整套已移除（含 `esdao`、`job` 包与 `spring-boot-starter-data-elasticsearch` 依赖）。`sql/create_table.sql` 里对应的建表语句还在，属残留，不要据此以为功能还存在。

微信公众号模块（`wxmp`、`WxMpController`）仍保留。

## 本地运行前置

`application.yml` 默认连 `localhost:3306/byte_judge`（root），库表用 `sql/create_table.sql` 初始化。现有测试都是连真实数据库的集成测试，没有可独立运行的单元测试。接口文档：`http://localhost:8101/api/doc.html`。
