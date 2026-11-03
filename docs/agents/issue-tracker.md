# Issue tracker: GitHub

本仓库的任务与规格使用 GitHub Issues，通过 gh CLI 操作。
目标仓库：jack-oliver-123/byte-judge。

## 常用操作

- 创建：gh issue create --repo jack-oliver-123/byte-judge --title "..." --body-file <file>
- 读取：gh issue view <number> --repo jack-oliver-123/byte-judge --json number,title,body,labels,comments
- 列表：gh issue list --repo jack-oliver-123/byte-judge --state open --json number,title,body,labels,assignees
- 更新正文：gh issue edit <number> --repo jack-oliver-123/byte-judge --body-file <file>
- 评论：gh issue comment <number> --repo jack-oliver-123/byte-judge --body-file <file>
- 标签：gh issue edit <number> --repo jack-oliver-123/byte-judge --add-label "..." 或 --remove-label "..."
- 关闭：先发表评论说明结果，再运行 gh issue close <number> --repo jack-oliver-123/byte-judge。

多行正文写入 UTF-8 临时文件，通过 --body-file 提交。
首次创建 issue 前核对目标仓库；所有写入显式指定上述 --repo。

## 父子关系与依赖

优先使用 GitHub 原生子 issue 与依赖关系：

- 创建子任务：gh issue create --parent <parent> ...
- 关联已有子任务：gh issue edit <parent> --add-sub-issue <child>
- 添加阻塞项：gh issue edit <child> --add-blocked-by <blocker>

以上命令均须附带目标 --repo。
若当前 CLI 或仓库不支持，子任务正文顶部写 Part of #<parent>，
父任务维护子任务清单；依赖在正文顶部写 Blocked by: #<n>。
所有阻塞任务关闭后，任务才可开始。

## Pull requests as a triage surface

**PRs as a request surface: no.**

## 技能指令映射

- “publish to the issue tracker”：创建 GitHub issue。
- “fetch the relevant ticket”：读取对应 issue 及评论。

## Wayfinding

- 地图：使用一个带 wayfinder:map 标签的 issue，维护 Notes、Decisions-so-far 和 Fog。
- 子任务：关联到地图，使用 wayfinder:research、wayfinder:prototype、
  wayfinder:grilling 或 wayfinder:task 标签。
- 选择任务：按地图顺序，选择未关闭、无负责人且无未关闭阻塞项的第一个子任务。
- 认领：通过 gh issue edit <number> --add-assignee @me 分配负责人。
- 完成：发表评论说明结果，关闭子任务，并在地图 Decisions-so-far 中记录结论和链接。

上述操作均使用本仓库；所需标签不存在时，按实际使用的技能约定创建。
