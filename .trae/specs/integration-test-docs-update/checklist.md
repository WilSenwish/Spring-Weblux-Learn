# Checklist

## 双栈端到端集成测试
- [x] Webflux 完整流程通过（注册→登录→创建账本→创建分类→记账）
- [x] WebMVC 完整流程通过（注册→登录→创建账本→创建分类→记账）
- [x] 双栈账单数据 >50 条（Webflux=197, WebMVC=120）
- [x] MySQL `bill` 表 count == MongoDB `bill_document` 集合 count
- [x] 双栈数据 count 都不为 0
- [x] 双栈同一业务流程结果一致（统计接口 weekly: income=2621.0, expense=166.0）

## 数据库简表文档 db-schema.md
- [x] `/docs/db-schema.md` 存在
- [x] 包含 `user` 表建表语句
- [x] 包含 `category` 表建表语句（含 ledger_id 字段）
- [x] 包含 `ledger` 表建表语句（含 allow_member_edit 字段）
- [x] 包含 `ledger_member` 表建表语句
- [x] 包含 `bill` 表建表语句（含 ledger_id 字段）
- [x] 包含 `bill_document` MongoDB 集合字段定义

## MySQL 表设计说明文档 mysql-design.md
- [x] `/docs/mysql-design.md` 存在
- [x] 包含 ER 关系图（Mermaid 文本）
- [x] 包含每个字段的约束理由
- [x] 包含索引设计原则
- [x] 包含命名规范说明

## Webflux 对照学习指南 webflux-learning-guide.md
- [x] `/docs/webflux-learning-guide.md` 存在
- [x] 包含项目对照学习路径
- [x] 包含核心概念对比表（Controller/Repository/Service/Exception/Configuration）
- [x] 包含 5 个典型场景的代码对照示例
- [x] 包含关键差异说明（阻塞点、错误传播、线程模型、背压）

## README.md 更新
- [x] README 包含账本功能说明
- [x] README 包含分类与账本绑定说明
- [x] README 包含 allowMemberEdit 说明
- [x] README 包含 JWT 公共提取说明
- [x] README 包含 Webflux 响应重构说明
- [x] README 包含跨平台 Docker 配置说明
- [x] README 包含学习指南入口链接
- [x] README 新增项目总结小节

## architecture-comparison.md 更新
- [x] 包含账本功能的双栈实现差异对比
- [x] 包含 allowMemberEdit 的双栈实现对比
- [x] 包含 JWT 公共提取的双栈差异

## api-reference.md 更新
- [x] 包含 `/api/ledgers` 全部接口
- [x] 包含 `/api/ledgers/{id}/members` 成员管理接口
- [x] 包含 BillRequest 新增字段说明
- [x] 包含 CategoryRequest 新增字段说明
- [x] 包含 LedgerRequest 新增字段说明

## mongodb-design.md 更新
- [x] 包含 BillDocument 完整字段定义（含 ledgerId）
- [x] 包含 MySQL bill 与 MongoDB bill_document 字段映射
- [x] 包含启动时 MongoDB 同步机制说明
