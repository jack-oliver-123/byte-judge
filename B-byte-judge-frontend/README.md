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

已实现工单 #22–#27 的编辑链路。修订冲突逐字段选择本地或服务器内容，确认后回到表单手动保存；只改变测试数据清单时也需确认修订。输入在 1 秒内暂存到当前标签页，刷新时先确认身份和服务器详情，同修订询问恢复，不同修订进入对照；未通过字段校验的输入也会保留。普通离开提醒，主动退出和换账户清理原账户暂存。存储或剪贴板不可用时可选择文本复制。

保存结果不确定时先读取服务器，不自动重发 PATCH：内容一致提示已同步，修订未变供人工重试，修订变化进入对照；回读仍失败则暂停编辑与保存。401 由管理员选择重新登录并返回确认恢复；40301 更新 CSRF 后只允许手动重试；40300 或编辑中 404 停止写入并保留复制出口。

题面、输入说明和输出说明使用 markdown-it 与 DOMPurify 在本地预览，原始 HTML 转义、链接仅允许 http/https，图片显示占位且不发请求。源文仍作为普通字符串保存。测试数据仅展示清单，上传、Java 21 发布校验、发布和下架尚未接入前端。真实接口、浏览器行为与故障注入的验证边界见[编辑页验证记录](../docs/frontend-B/problem-draft-edit-verification.md)。
