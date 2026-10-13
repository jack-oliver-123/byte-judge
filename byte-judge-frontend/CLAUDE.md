# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概况

Byte Judge 的前端工程，基于 **Vue 3 + TypeScript + Vue CLI 5 (webpack)**，UI 库为 **Arco Design Vue**。
目录位于 monorepo `byte-judge/` 之下，与 `byte-judge-backend/` 并列。

项目处于初始化阶段：已搭好通用布局、HTTP 请求层与全局权限骨架，业务功能尚未开始。

## 常用命令

```bash
npm install          # 安装依赖
npm run serve        # 启动 dev server（热更新，默认 http://localhost:8080）
npm run build        # 生产构建，输出到 dist/
npm run lint         # ESLint + Prettier 检查，默认自动修复（--fix 是默认行为）
```

单文件 lint（不走全量扫描）：

```bash
npx vue-cli-service lint src/components/GlobalHeader.vue
npx vue-cli-service lint --no-fix src/        # 只检查不改写
```

**类型检查**：没有独立的 type-check script，且覆盖范围有缺口（实测）：

- `.ts` 文件：`npx tsc --noEmit` 可秒级独立校验，远快于 `build`（~33s）。改 `.ts` 后优先用它。
- `.vue` 文件：**类型无人检查**。`tsc` 按扩展名跳过 `.vue`；`build` 的 fork-ts-checker 实测也放过 SFC 内的类型错误。改 `.vue` 时没有自动化兜底，要补齐需引入 `vue-tsc`。

**测试**：当前**未配置任何测试框架**（无 Jest / Vitest / Cypress，package.json 中无 `test` script）。如需加测试，要先引入 `@vue/cli-plugin-unit-jest` 或迁移到 Vitest。

## 架构要点

### 渲染链路

```
main.ts  →  App.vue  →  BasicLayout.vue  →  <router-view />
```

- `main.ts`：`createApp(App).use(ArcoVue).use(store, key).use(router)`，全量引入 `arco.css`，随后引入 `@/assets/global.css`（**顺序不能反**，否则全局重置会被 arco.css 盖掉），并 `import "@/access"` 注册权限守卫。
- `App.vue`：只是 `BasicLayout` 的壳，不含逻辑。
- `BasicLayout.vue`：`a-layout` 三段式（header / content / footer），`<router-view />` 挂在 content 内的 `.container` 里。所有页面都在这个布局里渲染。

布局上有三条约定，改样式时别推翻：

- **页脚落底靠 flex，不靠定位。** `.arco-layout` 是 `flex-direction: column`、`.arco-layout-content` 带 `flex: 1`，内容区会吃掉 `min-height: 100vh` 的剩余空间，自动把 `flex: 0 0 auto` 的页脚顶到底部。曾经给 footer 写过 `position: sticky; bottom: 0`——那实现的是"滚动时始终可见"，结果是页脚悬浮盖住正文末尾。sticky 该用在 header 上（现已如此）。
- **左右留白用 `--layout-padding-x`**（定义在 `#basicLayout` 上）。顶栏、内容、页脚三者必须严格对齐，散成三份字面量迟早改漏。
- **内容宽度上限 1200px**，由 `.container` 居中（`max-width` + `margin: 0 auto`）。想改通栏去掉 `max-width` 即可。页面组件自己不要再套一层容器。

### 路由与导航栏是强耦合的（重要）

路由被拆成几个文件，这是刻意设计：

- `src/router/route.ts` —— 导出 `routes: Array<RouteRecordRaw>`，**同时被 router 和导航栏复用**。
- `src/router/index.ts` —— 仅负责 `createRouter` + `createWebHistory`。
- `src/router/types.ts` —— `declare module "vue-router"` 扩展 `RouteMeta`（`title` / `access` / `hideInMenu`）。不做这步 `meta.access` 会被推导成 `unknown`。
- `src/router/paths.ts` —— 路径常量（如 `NO_AUTH_PATH`）。**零依赖**，为的是打破 `access/index.ts → @/router → route.ts` 的循环。

`GlobalHeader.vue` 直接 `import { routes } from "@/router/route"`，用 `v-for` 渲染菜单：

- 菜单项的 **key 取 `route.path`**，点击后 `router.push({ path: key })`；
- 菜单项的 **显示文字取 `route.meta.title`**（中文）；`route.name` 只用于命名式跳转；
- 菜单项经 `visibleRoutes` 这个 **computed** 过滤：排除 `meta.hideInMenu`，再过 `checkAccess`。必须是 computed，否则登录态变化后菜单不重算，表现为"权限没生效"。
- `router.afterEach` 同步 `selectedKeys`，保证刷新 / 前进后退时高亮正确。

因此：**新增一条 route 会自动在顶部导航多出一个菜单项**，除非给它加 `meta.hideInMenu: true` 或设置更高的 `meta.access`。

### 权限管理

四个角色：`NOT_LOGIN` / `USER` / `ADMIN` / `BAN`，定义在 `src/access/accessEnum.ts`（`as const` 对象 + 派生联合类型，不是 TS enum）。其中 `USER` / `ADMIN` / `BAN` 与后端 `UserRoleEnum` 的取值逐一对齐，`NOT_LOGIN` 是前端补的状态（后端没有这个角色），同时充当"公开页面"的 `needAccess` 取值。

`ACCESS_RANK` 声明为 `Record<AccessEnum, number>`，所以**新增角色时漏补等级会直接编译报错**——这是刻意的，避免运行时取到 `undefined` 后比较出 `NaN`。`isAccessEnum` 用 `value in ACCESS_RANK` 实现，意味着「合法角色清单」与「等级表」共用一份数据，加角色时这两件事必须一起改。

- `src/access/checkAccess.ts` —— **纯函数**，不读 store、不碰 router。守卫与导航栏菜单**共用这一个判权源**，避免出现"菜单看得见但点进去被拦"。后续引入 Vitest 可零成本单测。
- `src/access/index.ts` —— `router.beforeEach` 全局守卫。
- `meta.access` 缺省视为公开页面；`/noAuth`（403 页）的 `access` 必须是 `NOT_LOGIN`，否则守卫跳过去又被拦，无限重定向。
- 登录页尚未实现，未登录 / 权限不足 / 已封号**统一跳 `/noAuth`**，仅提示文案不同。`BAN` 单独一支是必要的：若也提示"请先登录"，用户会去重新登录，而 Session 本来有效，登完还是被拦。接入登录页后只需改守卫里"未登录"那一支的跳转目标（`src/router/paths.ts` 已留 `LOGIN_PATH` 注释）。

**核心设计：`LoginUser.userRole` 可选，用"字段缺省"编码登录态三态。**

| `userRole` | 含义 |
| --- | --- |
| 缺省（`undefined`） | 尚未向后端确认过登录态 |
| `NOT_LOGIN` | 已确认，确实未登录 |
| `USER` / `ADMIN` / `BAN` | 已登录（`BAN` 为已登录但被禁用） |

守卫据此决定是否发起 `getLoginUser`，所以整个会话只会请求一次。配套约束：**`getLoginUser` 在失败（未登录 / 网络异常 / 后端未启动）时也必须 commit** 一个 `userRole: NOT_LOGIN` 的用户——否则 `userRole` 永远缺省，每次路由跳转都会重发请求。后端返回未知角色时降级为 `NOT_LOGIN`（fail-closed），不要降级为 `USER`。

### HTTP 请求层

- `src/env.ts` —— **环境变量的唯一收口处**。`tsconfig` 的 `types` 只含 `webpack-env`，而它对 process 的声明是 `interface NodeProcess { env?: any }`，`process.env` 是 `any`、TS 零保护。所以业务代码一律 import 这里的常量，**不要直接写 `process.env.XXX`**；文件内对缺失变量做 fail-fast 抛错（DefinePlugin 是编译期文本替换，配错只会静默变成 `undefined`）。
- `src/request.ts` —— axios 实例 + 拦截器，另导出 `unwrap()` 与 `BusinessError`。
- `src/api/` —— `types.ts` 放 `BaseResponse<T>` 等契约类型，按模块拆接口函数（如 `user.ts`）。
- `src/generated/` —— `npm run gen:api` 的产物（openapi-typescript-codegen，读 `http://localhost:8101/api/v3/api-docs`，后端没启动会失败）。整个目录不要手改；唯一例外是 `core/request.ts`，它由 `scripts/openapi-request.ts` 经 `--request` 覆盖，把请求转发回 `src/request.ts` 复用 baseURL / withCredentials / `unwrap()`。要改请求行为改 `scripts/openapi-request.ts` 再重新生成，直接改 `core/request.ts` 会被覆盖。
- `.env.development` / `.env.production` —— 自定义变量**必须以 `VUE_APP_` 开头**才会注入客户端（`BASE_URL` / `NODE_ENV` 是内置例外）。这两个文件**进 git**，密钥放 `.env.local`（已被 `.gitignore` 忽略）。

两条刻意的设计约束：

- **响应拦截器不改变返回值结构**，不写 `return response.data`。一旦在拦截器解包，运行时值就与 `AxiosResponse<T>` 类型不符，只能靠 `declare module "axios"` 骗类型系统——在 `strict` 下是纯负债。`BaseResponse` 的解包交给 `unwrap()`，在接口函数里显式做。
- **拦截器不弹 toast**，只做错误信息归一化（写回 `error.message`）。`getLoginUser` 这类"探测型"请求失败属正常情况，无脑弹错会让每次进页面都跳红条。要不要提示用户由调用方决定。

### 后端契约（已核对 `byte-judge-backend` 源码）

鉴权方式**已确定：Spring Session + Cookie**（`application.yml` 的 `spring.session.timeout` 配了 30 天 Cookie；Redis `store-type` 还是注释状态）。因此 `src/request.ts` 的 `withCredentials: true` 已启用——跨域不带 Cookie 的话每次都是新会话、永远未登录。后端 CORS 已配 `allowCredentials(true)` + `allowedOriginPatterns("*")`；用 `patterns` 而非 `allowedOrigins("*")` 是必须的，后者与 `allowCredentials` 冲突会直接报错。

以下三条踩过坑，改接口时务必对着后端源码核：

- **字段名是 `userName`（大写 N），不是 `username`。** 后端 `LoginUserVO` 的 Java 字段为 `private String userName`，Jackson 序列化后 JSON key 即 `userName`。写错**不会有编译报错**（接口类型里写什么就是什么），只在运行时取到 `undefined`，表现为导航栏用户名空白。store 里的 `LoginUser.username` 是小写——前端状态命名归前端，与接口契约刻意不一致，`store/user.ts` 的 commit 处是唯一的转换点。
- **业务失败仍是 HTTP 200。** `GlobalExceptionHandler` 把 `BusinessException` 统一转成 `BaseResponse`，所以未登录是 `code: 40100`、无权限是 `code: 40101`，**永远不会出现 HTTP 401/403**。这意味着拦截器的 error 分支收不到登录态失效，这类处理要写在 `unwrap()` 里按 code 判断。
- **`/user/get/login` 未登录时抛错而非返回 null。** 后端 `getLoginUser` 直接 `throw NOT_LOGIN_ERROR`，经 `unwrap()` 抛成 `BusinessError`，由 `store/user.ts` 的 catch 兜住并 commit `NOT_LOGIN`。

接口前缀：后端 `server.port: 8101` + `context-path: /api`，与 `.env.development` 的 `http://localhost:8101/api` 对应。已有但前端未接的接口：`POST /user/login`、`POST /user/register`、`POST /user/logout`。

JWT 扩展点仍留在 `src/request.ts` 的请求拦截器里（注释状态），若将来换 Token 方案，同时去掉 `withCredentials`。

### 状态管理

**Vuex 4**，已接入 `user` 模块（`store/user.ts`，`namespaced: true`，存 `loginUser`）。类型集中在 `store/types.ts`——独立成文件是为了避免 `index.ts` ↔ `user.ts` 循环引用。

以下四条漏掉任何一条都**不会有编译报错**，只在运行时出问题：

- `main.ts` 必须 `app.use(store, key)`。`key` 是 `InjectionKey<Store<RootState>>`，Vuex 4 靠它给 `useStore()` 带类型；改回 `app.use(store)` 会让 `useStore()` 返回 `undefined`。
- `main.ts` 必须 `import "@/access"`，且在 `.use(router)` 之前。守卫靠**副作用导入**注册，漏掉这行权限完全静默失效。
- 组件内一律 `import { useStore } from "@/store"`（项目封装版，已带 `RootState`），**不要**从 `vuex` 直接导入。
- 新增模块要同时做两件事：注册进 `index.ts` 的 `modules`，并在 `types.ts` 的 `RootState` 上补字段。模块 state 是运行时合并的，TS 推导不出来。

模块外部 dispatch / commit 需带命名空间前缀（`store.dispatch("user/getLoginUser")`），模块内部用短名即可。

### UI 组件库

Arco Design Vue 已在 `main.ts` 中**全局全量注册**，模板里直接写 `a-layout`、`a-menu`、`a-button` 等即可，**不需要在 SFC 内 import**。

代价是产物体积：全量引入组件库 + `arco.css` 后，`chunk-vendors` 约 **980 KiB（gzip 275 KiB）**、vendor CSS 约 **390 KiB（gzip 48 KiB）**，而目前实际只用到了 `a-layout` / `a-menu` / `a-result` / `a-button` 与命令式的 `Message`。若后续在意首屏体积，可改为按需引入（`unplugin-vue-components` + `ArcoResolver`）；但随着业务页面增多、用到的组件变广，全量引入的相对浪费会逐渐摊薄——属于需要权衡的架构决策，不要随手切换。

注意：`Message`、`Notification` 这类命令式 API 即使改成按需引入也仍需手动 import（`src/access/index.ts` 已在用）。

## 代码约定

- **路径别名**：`@` → `src/`，在 `tsconfig.json` 的 `paths` 中定义，webpack 侧由 Vue CLI 默认提供。跨目录引用一律用 `@/xxx`。
- **组件写法**：新组件统一用 `<script setup lang="ts">`（`BasicLayout` / `GlobalHeader` / `App` 均为此风格）。`HomeView.vue` 仍是 Vue CLI 脚手架留下的 `defineComponent` Options API 写法，属历史残留，改动时应顺手迁移。
- **脚手架残留**：`components/HelloWorld.vue`、`views/AboutView.vue` 为 Vue CLI 初始化模板文件，接入真实业务页面时可直接删除。
- **TypeScript**：`strict: true` 已开启，避免 `any`。`.vue` 文件的模块声明在 `src/shims-vue.d.ts`。
- **格式化**：Prettier 通过 `eslint-plugin-prettier` 集成进 ESLint，**不要**单独跑 prettier，直接 `npm run lint` 即可。
- **样式**：各 SFC 使用 `<style scoped>`，并以 `#组件id` 作为根选择器前缀（如 `#basicLayout .header`）。无 CSS 预处理器。
- **全局样式**：只有 `src/assets/global.css`，且**只放 scoped 表达不了的规则**——即作用于 `html` / `body` 这类组件树之外节点的。组件样式一律留在各自 SFC 里，否则全局样式会膨胀成谁也不敢删的一坨。目前里面只有一条 `body { margin: 0 }`：浏览器默认的 8px body 外边距与 `min-height: 100vh` 叠加后会让页面恒高 `100vh + 16px`，于是永远挂一条纵向滚动条、四周露白边；`arco.css` 只定义主题变量，不含 html/body 重置。
- **颜色一律用 Arco 的 CSS 变量**（`--color-bg-2` / `--color-border-2` / `--color-text-1~4` / `--color-fill-1` 等），不要写死色值。这样将来切暗色主题（`body[arco-theme='dark']`）能自动跟随。注意 CSS 变量**没有兜底**，拼错会静默失效、样式直接消失——加新变量前先在 `arco.css` 里确认存在。
- `no-console` / `no-debugger` 仅在 `NODE_ENV=production` 时告警，开发期不拦截。

## 待补齐的基础设施

以下在当前代码中**尚不存在**，新增相关功能时需从零搭建，不要假设已有：

- 登录 / 注册页面。`src/views/` 下只有脚手架页与 `NoAuthView.vue`（403）。后端的 `POST /user/login`、`POST /user/register`、`POST /user/logout` 均已就绪，前端尚未接。
- 测试框架。`src/access/checkAccess.ts` 是纯函数，是最该先补测试的地方。
- `.vue` 的类型检查（需 `vue-tsc`）

后端服务在 monorepo 的 `byte-judge-backend/`（Spring Boot，端口 8101）。**后端未启动时 `getLoginUser` 必然失败并降级为未登录——页面显示"未登录"不是 bug**。

### 已知待办

- `src/access/checkAccess.ts` 的核心判权逻辑是 `return false` 占位（fail-closed），文件内 TODO 列了「线性等级 vs 精确匹配」两种方案。**在此之前，所有需要登录的页面都会被拒绝**。ESLint 对 `userRole` 的 unused 告警即是该待办的标记。
- `GlobalHeader.vue` 的菜单用 Arco `a-menu`，其内部的自动折叠逻辑（`es/menu/overflow-wrap.js`）依赖 ResizeObserver。**瞬时改变视口宽度**时 dev server 的错误遮罩可能弹出 `ResizeObserver loop completed with undelivered notifications`——这是浏览器层面的良性通知（回调被推迟到下一帧，没有丢事件），页面加载与正常拖拽窗口都不会触发，生产构建也没有这层遮罩。
- 接口契约是**对着后端源码核对**的，但**未做端到端联调**（核对时后端未启动）。首次跑通后端后需复验三件事：Cookie 是否随请求带上、`userName` 能否正确显示、未登录时 `code: 40100` 是否按预期走到 catch 分支。
