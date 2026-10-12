# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概况

Byte Judge 的前端工程，基于 **Vue 3 + TypeScript + Vue CLI 5 (webpack)**，UI 库为 **Arco Design Vue**。
目录位于 monorepo `byte-judge/` 之下（当前仅有 `byte-judge-frontend/` 一个子项目）。

项目处于初始化阶段，仅搭好通用布局骨架，业务功能尚未开始。

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

**类型检查**：没有独立的 type-check script。TypeScript 校验由 `@vue/cli-plugin-typescript` 的 fork-ts-checker 在 `serve` / `build` 过程中完成，因此类型错误只会在跑这两个命令时暴露。

**测试**：当前**未配置任何测试框架**（无 Jest / Vitest / Cypress，package.json 中无 `test` script）。如需加测试，要先引入 `@vue/cli-plugin-unit-jest` 或迁移到 Vitest。

## 架构要点

### 渲染链路

```
main.ts  →  App.vue  →  BasicLayout.vue  →  <router-view />
```

- `main.ts`：`createApp(App).use(ArcoVue).use(store).use(router)`，并全量引入 `arco.css`。
- `App.vue`：只是 `BasicLayout` 的壳，不含逻辑。
- `BasicLayout.vue`：`a-layout` 三段式（header / content / footer），`<router-view />` 挂在 content 内。所有页面都在这个布局里渲染。

### 路由与导航栏是强耦合的（重要）

路由被拆成两个文件，这是刻意设计：

- `src/router/route.ts` —— 导出 `routes: Array<RouteRecordRaw>`，**同时被 router 和导航栏复用**。
- `src/router/index.ts` —— 仅负责 `createRouter` + `createWebHistory`。

`GlobalHeader.vue` 直接 `import { routes } from "@/router/route"`，用 `v-for` 渲染菜单：

- 菜单项的 **key 取 `route.path`**，点击后 `router.push({ path: key })`；
- 菜单项的 **显示文字取 `route.name`**；
- `router.afterEach` 同步 `selectedKeys`，保证刷新 / 前进后退时高亮正确。

因此：**新增一条 route 会自动在顶部导航多出一个菜单项**。目前没有任何过滤逻辑，若将来需要隐藏某些路由（如登录页、详情页、需权限的页面），必须在 `GlobalHeader.vue` 里加筛选（典型做法是在 route 的 `meta` 上加 `hideInMenu` / `access` 字段后过滤）。

### 状态管理

`src/store/index.ts` 是 **Vuex 4** 的空壳（state / getters / mutations / actions / modules 全为空）。尚未接入任何业务状态。按 `modules` 字段的预留，后续应按模块拆分（如 `store/user.ts`）再注册进来。

### UI 组件库

Arco Design Vue 已在 `main.ts` 中**全局全量注册**，模板里直接写 `a-layout`、`a-menu`、`a-button` 等即可，**不需要在 SFC 内 import**。

代价是产物体积：全量引入组件库 + `arco.css` 后，`chunk-vendors` 约 **980 KiB（gzip 275 KiB）**、vendor CSS 约 **390 KiB（gzip 48 KiB）**，而目前实际只用到了 `a-layout` 和 `a-menu`。若后续在意首屏体积，可改为按需引入（`unplugin-vue-components` + `ArcoResolver`）；但随着业务页面增多、用到的组件变广，全量引入的相对浪费会逐渐摊薄——属于需要权衡的架构决策，不要随手切换。

注意：`Message`、`Notification` 这类命令式 API 即使改成按需引入也仍需手动 import。

## 代码约定

- **路径别名**：`@` → `src/`，在 `tsconfig.json` 的 `paths` 中定义，webpack 侧由 Vue CLI 默认提供。跨目录引用一律用 `@/xxx`。
- **组件写法**：新组件统一用 `<script setup lang="ts">`（`BasicLayout` / `GlobalHeader` / `App` 均为此风格）。`HomeView.vue` 仍是 Vue CLI 脚手架留下的 `defineComponent` Options API 写法，属历史残留，改动时应顺手迁移。
- **脚手架残留**：`components/HelloWorld.vue`、`views/AboutView.vue` 为 Vue CLI 初始化模板文件，接入真实业务页面时可直接删除。
- **TypeScript**：`strict: true` 已开启，避免 `any`。`.vue` 文件的模块声明在 `src/shims-vue.d.ts`。
- **格式化**：Prettier 通过 `eslint-plugin-prettier` 集成进 ESLint，**不要**单独跑 prettier，直接 `npm run lint` 即可。
- **样式**：各 SFC 使用 `<style scoped>`，并以 `#组件id` 作为根选择器前缀（如 `#basicLayout .header`）。目前无全局样式文件与 CSS 预处理器。
- `no-console` / `no-debugger` 仅在 `NODE_ENV=production` 时告警，开发期不拦截。

## 待补齐的基础设施

以下在当前代码中**尚不存在**，新增相关功能时需从零搭建，不要假设已有：

- HTTP 请求层（无 axios / 无 `src/api` 目录 / 无 `.env` 环境变量配置）
- 用户登录态与权限控制（`meta.access` 之类的路由守卫）
- 测试框架
