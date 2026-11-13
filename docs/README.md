# 文档目录规范

项目文档统一存放在仓库根目录 `docs/`，按下表选择固定位置。各子项目内不保留 `docs/` 目录。

## 固定路径

以下路径均相对仓库根目录。

| 文档类型 | 固定位置 |
| --- | --- |
| 公共调研、需求、技术选型、架构与项目结构 | `docs/` 下现有编号文档 |
| 功能规格与验收标准 | [docs/specs/](specs/) |
| 架构决策记录 | [docs/adr/](adr/) |
| Agent 协作规范 | [docs/agents/](agents/) |
| 公共图片与附件 | [docs/assets/](assets/) |
| 后端接口、设计与操作专题 | [docs/backend/](backend/) |
| B 端页面、交互与接入说明 | [docs/frontend-B/](frontend-B/) |
| C 端页面、交互与接入说明 | [docs/fontend-C/](fontend-C/) |

C 端目录沿用已有名称 `fontend-C`。跨端共享的功能规格保存在 `docs/specs/`，各端专题文档通过相对链接引用。

各级 `README.md`、`AGENTS.md`、`CLAUDE.md` 保留原位，根目录 `GLOSSARY.md` 继续作为共享词汇表。子项目与基础设施目录中的 `README.md` 保留就近启动、构建、配置及验证说明；独立专题文档放入上表对应目录。

## 引用规则

- Markdown 相对链接以当前文档所在目录为基准，路径统一使用 `/`；保留链接的章节锚点。
- 后端工程说明引用接口文档时使用 `../docs/backend/<文件名>.md`；B/C 端工程说明分别引用 `../docs/frontend-B/`、`../docs/fontend-C/` 下的文档。
- 迁移文档时保留文件名与正文，仅调整存放位置及必要的引用路径；同步检查移入文档的出链和其他文件指向它的入链。
- 迁移完成后检查相对链接目标及章节锚点，确认子项目内的原 `docs/` 目录已移除。

## 后端文档索引

- [管理员受控初始化](backend/admin-initialization.md)
- [登录、当前身份、退出与管理员权限接口](backend/auth-api.md)
- [题目草稿管理接口](backend/problem-draft-api.md)
- [后端启动与验证记录](../byte-judge-backend/README.md)

## 功能规格索引

- [规格 003：B 端题目草稿编辑与并发保存](specs/003-admin-problem-draft-edit.md)

## 前端文档索引

- [B 端题目草稿编辑与并发保存设计](frontend-B/problem-draft-edit-design.md)
- [B 端题目草稿编辑与并发保存验证记录](frontend-B/problem-draft-edit-verification.md)
- [B 端管理员登录与题库管理静态原型](frontend-B/prototypes/README.md)
- [B 端第二版白色与淡青色原型](frontend-B/prototypes-v2/README.md)
- [B 端纸墨松绿视觉原型](frontend-B/prototypes-pine/README.md)

C 端尚无独立专题文档，对应目录暂以 `.gitkeep` 保留。
