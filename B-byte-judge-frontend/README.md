# Byte Judge 管理端（B 端）

面向管理员的前端工程，使用 Vue 官方脚手架 `create-vue`（`npm create vue@latest`）初始化。

## 技术栈

- Vue 3 + TypeScript + Vite
- Vue Router（`createWebHistory`）
- Pinia
- shadcn/vue（Reka UI）+ Tailwind CSS 4
- Lucide 图标 + Vue Sonner 通知
- ESLint（含 oxlint）+ Prettier

## 环境要求

- Node.js `^22.18.0 || >=24.12.0`（见 `package.json` 的 `engines`）

## 常用命令

```sh
npm install        # 安装依赖
npm run dev        # 启动开发服务器
npm run build      # 类型检查并构建生产包
npm run preview    # 预览构建产物
npm run lint       # 代码检查
npm run format     # 代码格式化
```

## 部署约定

- 构建基址固定为 `/admin/`（`vite.config.ts` 的 `base`），与 `../docs/04.系统架构设计.md` §2.3 的 `/admin/`、`/admin/assets/**` 静态资源路径一致。
- 业务请求统一走 `/api/v1/**`，由网关转发；前端不直连业务服务。
- 本地 `npm run dev` 将 `/api/v1/**` 代理至 `http://127.0.0.1:8080`；生产环境使用同源 HTTPS。

## 当前状态

已实现管理员登录、身份恢复与退出、管理员路由守卫、题库草稿列表/详情/新建/逻辑删除。全部基础控件使用 shadcn/vue，采用暖白、钴蓝与青柠主题；代码卡片浮动、轨迹与输入光标、页面入场和表格反馈均支持系统减少动态效果设置。

基础组件源码位于 `src/components/ui/`，可通过 `npx shadcn-vue@latest add <组件名>` 添加；全局令牌与动效位于 `src/styles/theme.css`。列表统计按当前查询范围及本页实际数据展示，不模拟发布数据。

题库列表与详情支持进入 `/problems/:id/edit` 独立编辑页，完整字段通过真实网关和 CSRF 手动保存，以返回详情更新草稿修订号。列表筛选、页码与每页条数保存在 URL，返回题库可恢复。字段错误就近展示；并发冲突、身份失效和结果不确定时保留输入并停止重放。保存中冻结字段并阻止普通站内跳转。

已实现工单 #22 的基础编辑保存和 #23 的共享 CSRF、失效后手动重试及错误分类。40301 后仅更新令牌，由管理员再次点击保存；401、权限拒绝、字段拒绝与结果未知分别提示，保留请求编号和冲突详情。本地暂存、冲突字段合并、保存结果回读确认、身份异常恢复和 Markdown 预览由 #24–#27 继续实现。离开前需保存或复制输入。测试数据仅展示清单，上传、Java 21 发布校验、发布和下架尚未实现。验证范围见[编辑页验证记录](../docs/frontend-B/problem-draft-edit-verification.md)。
