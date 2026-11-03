# Domain docs

本仓库采用 single-context 布局：
共享词汇表位于根目录 GLOSSARY.md，架构决策位于 docs/adr/。

## 读取规则

探索领域模型或提出架构变更前：

1. 若存在 GLOSSARY.md，读取其中的术语定义。
2. 若存在 docs/adr/，读取与当前工作相关的 ADR。
3. 若以后引入 GLOSSARY-MAP.md，按映射读取相关上下文的词汇表与 ADR。

文件尚不存在时直接继续。由 domain-modeling 技能在实际形成
术语共识或架构决策时创建，不为初始化生成空白文档。

## 使用规则

- issue、设计方案、代码和测试中的领域概念沿用词汇表定义。
- 遇到缺失术语，先核对项目现有用法；确有缺口时记录供 domain-modeling 处理。
- 方案与已有 ADR 冲突时，明确指出对应 ADR、冲突内容和重新讨论的理由。
