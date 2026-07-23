# Tasks

- [x] Task 1: 双栈端到端集成测试
  - [x] SubTask 1.1: 启动双栈后端（Webflux 8080 + WebMVC 8081）
  - [x] SubTask 1.2: 执行完整业务流程测试（注册→登录→创建账本→创建自定义分类→记账）
  - [x] SubTask 1.3: 循环创建 >50 条账单数据
  - [x] SubTask 1.4: 验证 MySQL `bill` 表 count == MongoDB `bill_document` 集合 count
  - [x] SubTask 1.5: 双栈结果一致性验证（同一业务流程在两套后端结果一致）

- [x] Task 2: 生成数据库简表文档 db-schema.md
  - [x] SubTask 2.1: 编写 `/docs/db-schema.md`，包含 user、category、ledger、ledger_member、bill 共 5 张 MySQL 表的建表语句
  - [x] SubTask 2.2: 包含 `bill_document` MongoDB 集合的字段定义
  - [x] SubTask 2.3: 包含字段说明、类型、注释、索引

- [x] Task 3: 生成 MySQL 表设计说明文档 mysql-design.md
  - [x] SubTask 3.1: 编写 `/docs/mysql-design.md`，包含 ER 关系图（Mermaid 文本）
  - [x] SubTask 3.2: 包含每个字段的约束理由
  - [x] SubTask 3.3: 包含索引设计原则
  - [x] SubTask 3.4: 包含命名规范说明

- [x] Task 4: 生成 Webflux 对照学习指南 webflux-learning-guide.md
  - [x] SubTask 4.1: 编写 `/docs/webflux-learning-guide.md`，包含项目对照学习路径
  - [x] SubTask 4.2: 包含核心概念对比表
  - [x] SubTask 4.3: 包含 5 个典型场景的代码对照示例
  - [x] SubTask 4.4: 包含关键差异（阻塞点、错误传播、线程模型、背压）

- [x] Task 5: 更新 README.md
  - [x] SubTask 5.1: 补充账本功能、分类与账本绑定、allowMemberEdit 说明
  - [x] SubTask 5.2: 补充 JWT 公共提取、Webflux 响应重构说明
  - [x] SubTask 5.3: 补充跨平台 Docker 配置文件说明
  - [x] SubTask 5.4: 顶部/末尾添加学习指南入口链接 + 项目总结小节

- [x] Task 6: 更新 architecture-comparison.md
  - [x] SubTask 6.1: 补充账本功能的双栈实现差异
  - [x] SubTask 6.2: 补充 allowMemberEdit 的双栈实现
  - [x] SubTask 6.3: 补充 JWT 公共提取的双栈差异

- [x] Task 7: 更新 api-reference.md
  - [x] SubTask 7.1: 补充 /api/ledgers 全部 REST 接口
  - [x] SubTask 7.2: 补充 /api/ledgers/{id}/members 成员管理接口
  - [x] SubTask 7.3: 补充 BillRequest、CategoryRequest、LedgerRequest 新增字段

- [x] Task 8: 更新 mongodb-design.md
  - [x] SubTask 8.1: 补充 BillDocument 完整字段定义（含 ledgerId）
  - [x] SubTask 8.2: 补充 MySQL bill 与 MongoDB bill_document 字段映射
  - [x] SubTask 8.3: 补充启动时 MongoDB 同步机制说明

# Task Dependencies
- Task 1 独立
- Task 2, 3, 4, 5, 6, 7, 8 文档任务可并行
