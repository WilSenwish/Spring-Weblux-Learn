# 双栈端到端测试 + 数据库简表 + 文档更新 Spec

## Why
1. 现有 docs/README.md 是早期版本，未体现账本、分类按账本绑定、allowMemberEdit、JWT 公共提取、Webflux 响应重构等近期新增功能
2. 缺少数据库表结构简表文档（建表语句汇总）
3. 需要双栈端到端集成测试覆盖注册→登录→创建账本→创建分类→记账全流程，账单数据 >50 条，验证 MySQL/MongoDB 账单明细一致性
4. 需要补充 MySQL 表设计说明（含 ER 关系、字段约束、索引设计原则）
5. 项目作为 Spring Webflux 学习案例，需要一份"对照学习指南"帮助读者通过 WebMvc vs Webflux 同一业务代码对比，高效学习 Webflux 异步非阻塞编程

## What Changes
- 双栈端到端集成测试：使用 curl 脚本（或测试类）覆盖完整业务流
- 记账数据预置 + 测试：确保 MySQL 和 MongoDB 账单明细 count 一致且 >50
- 新增 `/docs/db-schema.md`：根据实体类生成简表语句（user、category、ledger、ledger_member、bill、bill_document）
- 新增 `/docs/mysql-design.md`：MySQL 表设计说明（ER 关系图、字段约束理由、索引设计原则、命名规范）
- 新增 `/docs/webflux-learning-guide.md`：Webflux 对照学习指南（通过 WebMvc vs Webflux 同一业务代码对比，帮助读者高效学习 Webflux 异步非阻塞编程）
- 整体更新 `/docs/README.md`：体现新功能 + 学习路径指引
- 更新 `/docs/architecture-comparison.md`：补充账本功能、allowMemberEdit、JWT 公共提取的对比
- 更新 `/docs/api-reference.md`：补充账本相关接口
- 更新 `/docs/mongodb-design.md`：补充 BillDocument 字段说明（含 ledgerId、allowMemberEdit 相关）

## Impact
- 受影响模块: `accounting-webflux-backend`、`accounting-webmvc-backend`
- 新增/修改文档: `docs/db-schema.md`、`docs/mysql-design.md`、`docs/webflux-learning-guide.md`、更新 `docs/README.md` 等

## ADDED Requirements

### Requirement: 双栈端到端集成测试
执行注册→登录→创建账本→创建分类→记账完整流程，双栈均要通过，账单总数 >50。

#### Scenario: Webflux 完整业务流程通过
- **WHEN** 执行 Webflux 端到端测试
- **THEN** 注册、登录、创建账本、创建分类、记账接口全部成功
- **AND** 最终账单数据 >50 条

#### Scenario: WebMVC 完整业务流程通过
- **WHEN** 执行 WebMVC 端到端测试
- **THEN** 注册、登录、创建账本、创建分类、记账接口全部成功
- **AND** 最终账单数据 >50 条

#### Scenario: MySQL 与 MongoDB 账单明细一致
- **WHEN** 双栈完成记账后
- **THEN** MySQL `bill` 表 count == MongoDB `bill_document` 集合 count
- **AND** 两条数据 count 都不为 0

---

### Requirement: 数据库简表文档
根据实体类生成简表语句文档。

#### Scenario: 简表文档覆盖所有业务表
- **WHEN** 查看 `/docs/db-schema.md`
- **THEN** 包含 user、category、ledger、ledger_member、bill、bill_document 共 6 张表/集合的建表/建集合语句
- **AND** 每个表包含字段说明、类型、注释、索引

---

### Requirement: MySQL 表设计说明文档
新增 `/docs/mysql-design.md`，包含 ER 关系、字段约束、索引设计原则。

#### Scenario: MySQL 设计文档完整
- **WHEN** 查看 `/docs/mysql-design.md`
- **THEN** 包含：ER 关系图（user 1:N category 1:N bill；user 1:N ledger 1:N bill；user M:N ledger via ledger_member）
- **AND** 包含每个字段的约束理由（如 username UNIQUE、category 联合唯一约束 uk_user_ledger_name_type）
- **AND** 包含索引设计原则（高频查询、复合索引最左前缀、外键列加索引）
- **AND** 包含命名规范（snake_case 表名/字段名、id 为主键、created_at/updated_at 时间戳约定）

---

### Requirement: Webflux 对照学习指南
新增 `/docs/webflux-learning-guide.md`，通过 WebMvc vs Webflux 同一业务代码对比学习。

#### Scenario: 学习指南覆盖核心概念
- **WHEN** 查看 `/docs/webflux-learning-guide.md`
- **THEN** 包含：项目对照学习路径（先看 WebMVC 同步版本 → 再看 Webflux 响应式版本）
- **AND** 包含核心概念对比表：Controller 返回值（Xxx vs Mono<Xxx>）、Repository（MyBatis Mapper vs R2DBC Repository）、Service（同步阻塞 vs Mono/Flux 链式）、异常处理（ResponseEntityExceptionHandler vs ReactiveExceptionHandler）、配置（@Configuration vs @Configuration + @EnableWebFlux）
- **AND** 包含典型场景对照示例：
  - 场景 1：单条数据查询（findById）
  - 场景 2：列表 + 条件过滤
  - 场景 3：创建 + 同步多个数据源（MySQL + MongoDB + Redis）
  - 场景 4：事务处理（@Transactional vs R2DBC 事务）
  - 场景 5：分页查询（PageHelper vs Mono<PageResult>）
- **AND** 包含关键差异：阻塞点识别（不应在响应式链中调用 .block()）、错误传播（onErrorResume vs try-catch）、线程模型（NIO EventLoop vs Servlet 线程池）、背压（Reactive Streams 自动背压 vs 阻塞队列无背压）

#### Scenario: 学习指南对读者的指导价值
- **WHEN** 读者阅读本指南后尝试阅读 Webflux 代码
- **THEN** 能通过"对照 WebMVC 同步实现"快速理解 Webflux 异步非阻塞的语义
- **AND** 能识别响应式链中的潜在阻塞点
- **AND** 能区分"何时该用 Webflux"和"何时该用 WebMVC"（不要为了响应式而响应式）

---

### Requirement: 项目设计文档整体更新
更新 README、architecture-comparison、api-reference、mongodb-design，体现新增功能 + 学习路径。

#### Scenario: README 反映新功能 + 学习路径
- **WHEN** 查看 README.md
- **THEN** 包含账本功能、分类与账本绑定、allowMemberEdit、JWT 公共提取、Webflux 响应重构说明
- **AND** 顶部导航或末尾链接到 webflux-learning-guide.md（学习指南入口）
- **AND** 突出"双栈同业务"是本项目最大特色

#### Scenario: architecture-comparison 补充对比
- **WHEN** 查看 architecture-comparison.md
- **THEN** 包含账本功能、allowMemberEdit、JWT 公共提取的 Webflux vs WebMVC 实现差异

#### Scenario: api-reference 补充账本接口
- **WHEN** 查看 api-reference.md
- **THEN** 包含 /api/ledgers 全部 8 个 REST 接口的详细说明

#### Scenario: mongodb-design 补充 BillDocument 字段
- **WHEN** 查看 mongodb-design.md
- **THEN** 包含 BillDocument 完整字段定义（含 ledgerId），以及与 MySQL bill 表的字段映射关系
