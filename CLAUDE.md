# CLAUDE.md

## 仓库结构

- `byte-judge-backend/` — 后端。
- `B-byte-judge-frontend/` — B 端前端。
- `C-byte-judge-frontend/` — C 端前端。
- `docs/` — 调研、需求分析与需求规格文档。

## 当前阶段

- 项目已完成需求与技术选型方向确认，尚未完成概要设计、技术原型或业务实现验收。
- 修改业务范围、角色、流程或验收标准时，读取 `docs/02.需求规格说明书.md`；当前 V1.2 首期仅支持 Java 21 判题。
- 确定依赖、服务通信、存储、判题或部署方案时，读取 `docs/03.技术选型调研.md`，沿用用户已确认方向并完成其中的原型验证；具体补丁、制品和配额尚未锁定。
- 现有模板文件、依赖与历史说明不代表已确认方案；实际构建命令在可运行工程建立后补充。
- 各子项目的具体约定记录在对应目录的 `CLAUDE.md`，共享背景以本文件为准。

## Agent skills

### Issue tracker

任务与规格使用 GitHub Issues；读取、创建或更新任务前，读取 `docs/agents/issue-tracker.md`。

### Domain docs

采用 single-context 布局；探索领域模型或提出架构变更前，读取 `docs/agents/domain.md`。
