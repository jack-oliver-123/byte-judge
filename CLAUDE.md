# CLAUDE.md

## 仓库结构

- `byte-judge-backend/` — 后端。
- `B-byte-judge-frontend/` — B 端前端。
- `C-byte-judge-frontend/` — C 端前端。
- `docs/` — 调研、需求分析与需求规格文档。

## 提交与 PR 约定

- 提交代码（git commit message 末尾）和创建 PR 时都要写上 HiFox 任务编号（`ID-xxx`，如 `Fixes ID-123` 或 `Part of ID-123`，只写 `ID-123` 也可）。此外，在 PR 的标题、描述或来源分支名里写上任务编号可让该 PR 自动关联对应任务。

## 当前阶段

- 项目已有需求规格、技术选型、架构设计和前后端工程底座；已记录的构建与启动检查不代表完整技术原型或业务验收通过。
- 修改服务边界、接口路径或部署关系前，读取 `docs/04.系统架构设计.md`；调整模块、分包或依赖归属前，读取 `docs/05.项目结构.md`。
- 修改业务范围、角色、流程或验收标准时，读取 `docs/02.需求规格说明书.md`；当前 V1.2 首期仅支持 Java 21 判题。
- 确定依赖、服务通信、存储、判题或部署方案时，读取 `docs/03.技术选型调研.md`，沿用用户已确认方向并完成其中的原型验证；具体补丁、制品和配额尚未锁定。
- 构建、启动与验证命令见各子项目说明；后端实测记录见 `byte-judge-backend/README.md`，区分设计目标、依赖接入和实际验证结果。
- 各子项目的具体约定记录在对应目录的 `CLAUDE.md`，共享背景以本文件为准。

## Agent skills

### Issue tracker

任务与规格使用 GitHub Issues；读取、创建或更新任务前，读取 `docs/agents/issue-tracker.md`。

### Domain docs

采用 single-context 布局；探索领域模型或提出架构变更前，读取 `docs/agents/domain.md`。
