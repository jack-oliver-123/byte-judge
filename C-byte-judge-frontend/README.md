# Byte Judge 用户端（C 端）

面向用户的前端工程，使用 Vue 官方脚手架 `create-vue`（`npm create vue@latest`）初始化。

## 技术栈

- Vue 3 + TypeScript + Vite
- Vue Router（`createWebHistory`）
- Pinia
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

- 部署在站点根路径 `/`，与 `docs/04.系统架构设计.md` §2.3 的 `/`、`/assets/**` 静态资源路径一致。
- 业务请求统一走 `/api/v1/**`，由网关转发；前端不直连业务服务。

## 当前状态

仅完成工程底座初始化（脚手架、路由、状态管理、代码规范），尚未实现业务页面与请求层。
