# CLAUDE.md

- 本目录为 C 端前端，仓库结构与当前阶段见 `../CLAUDE.md`。
- 技术栈沿用 `../docs/03.技术选型调研.md`：Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus；工程由官方脚手架 `create-vue` 初始化。
- 部署在站点根路径 `/`，部署约定见 `../docs/04.系统架构设计.md` §2.3。
- 业务请求统一走 `/api/v1/**` 网关路径，前端不直连业务服务。
- 当前仅完成工程底座初始化，业务页面、请求层与 Element Plus 组件接入待实现。
