# CLAUDE.md

- 本目录为 B 端前端，仓库结构与当前阶段见 `../CLAUDE.md`。
- 技术栈为 Vue 3、TypeScript、Vite、Vue Router、Pinia、shadcn/vue（Reka UI）、Tailwind CSS 4；工程由官方脚手架 `create-vue` 初始化。用户已确认 B 端由 Element Plus 迁移至 shadcn/vue，C 端选型不变。
- 构建基址为 `/admin/`（`vite.config.ts` 的 `base`），部署约定见 `../docs/04.系统架构设计.md` §2.3。
- 业务请求统一走 `/api/v1/**` 网关路径，前端不直连业务服务。
- 基础组件通过 shadcn/vue CLI 安装在 `src/components/ui/`，业务页面与全局设计令牌分别位于 `src/views/`、`src/styles/theme.css`。组件使用 Lucide 图标、Sonner 通知；新增组件继续使用 shadcn/vue。
- 已接入登录、题库草稿管理与基础编辑保存页面、请求层与管理员路由守卫；冲突合并、暂存恢复、Markdown 预览与异常恢复待后续前端切片，测试数据上传、校验、发布与下架尚未接入。
- 本地开发时 `/api/v1/**` 经 Vite 代理到 `http://127.0.0.1:8080`；正式部署使用同源 HTTPS。
