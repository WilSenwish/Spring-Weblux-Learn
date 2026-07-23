# 双栈对齐修复 + 账本功能 Spec

## Why
当前双栈后端存在命名不对齐、数据不一致（salt 字段、category user_id）、MongoDB 与 MySQL 账单明细可能不同步等问题。同时需要新增账本功能，支持多用户共享账本和一人多账本。

## What Changes
- 主启动类命名对齐：`AccountingApplication` → `AccountingWebfluxApplication`
- 移除 `user` 表 `salt` 字段（BCrypt 自带盐值，无需额外 salt），删除实体类中 `salt` 字段
- 修复预设分类重复初始化问题：预设分类的 `user_id` 从 NULL 改为 0，使唯一约束 `uk_user_name_type` 能正确防重；`data.sql` 使用 `INSERT ... ON DUPLICATE KEY UPDATE` 确保幂等
- 修复 MongoDB 与 MySQL 账单明细同步问题：`updateBill` 时若 MongoDB 文档不存在则补建；`deleteBill` 时即使 MongoDB 文档不存在也不报错
- 新增账本（Ledger）功能：支持个人账本和共享账本，一人可有多账本，多用户可共用一账本
- **BREAKING**: `bill` 表新增 `ledger_id` 字段；新增 `ledger` 和 `ledger_member` 表；`BillDocument` 新增 `ledgerId` 字段；预设分类 `user_id` 从 NULL 改为 0

## Impact
- 受影响模块: `accounting-common`、`accounting-webflux-backend`、`accounting-webmvc-backend`
- 受影响代码: 主启动类、`User` 实体、`Bill` 实体、`BillDocument` 实体、`schema.sql`、`data.sql`、`BillService`、`StatisticsService`、新增 `LedgerService`/`LedgerController`、新增 `Ledger`/`LedgerMember` 实体

## ADDED Requirements

### Requirement: 账本（Ledger）功能

系统支持账本管理。每个用户注册时自动创建一个默认个人账本。用户可创建多个账本，可将其他用户邀请为账本成员。

#### 数据模型

**ledger 表**
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| name | VARCHAR(50) NOT NULL | 账本名称 |
| description | VARCHAR(255) | 账本描述 |
| owner_id | BIGINT NOT NULL | 创建者用户 ID |
| type | TINYINT NOT NULL DEFAULT 1 | 1-个人 2-共享 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：`idx_owner_id (owner_id)`

**ledger_member 表**
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| ledger_id | BIGINT NOT NULL | 账本 ID |
| user_id | BIGINT NOT NULL | 用户 ID |
| role | TINYINT NOT NULL DEFAULT 3 | 1-所有者 2-管理员 3-普通成员 |
| joined_at | DATETIME | 加入时间 |

索引：`idx_ledger_id (ledger_id)`、`idx_user_id (user_id)`、`uk_ledger_user (ledger_id, user_id)`

**bill 表变更**: 新增 `ledger_id BIGINT COMMENT '账本ID'`，索引 `idx_ledger_id (ledger_id)`

#### 实体类

新增到 `accounting-common`：
- `entity/Ledger.java` — `@Table("ledger")`，字段：id, name, description, ownerId, type, createdAt, updatedAt
- `entity/LedgerMember.java` — `@Table("ledger_member")`，字段：id, ledgerId, userId, role, joinedAt

`entity/Bill.java` 和 `entity/BillDocument.java` 新增 `ledgerId` 字段。

新增 DTO：
- `dto/LedgerRequest.java` — name, description, type
- `dto/LedgerMemberRequest.java` — userId, role

#### API 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ledgers` | 创建账本 |
| GET | `/api/ledgers` | 获取当前用户的账本列表（含共享的） |
| GET | `/api/ledgers/{id}` | 获取账本详情 |
| PUT | `/api/ledgers/{id}` | 更新账本（仅所有者/管理员） |
| DELETE | `/api/ledgers/{id}` | 删除账本（仅所有者） |
| GET | `/api/ledgers/{id}/members` | 获取账本成员列表 |
| POST | `/api/ledgers/{id}/members` | 邀请成员（仅所有者/管理员） |
| DELETE | `/api/ledgers/{id}/members/{userId}` | 移除成员（仅所有者/管理员，不能移除所有者） |

#### 账单关联账本

- `BillRequest` 新增 `ledgerId` 字段（可选，不传则使用用户默认个人账本）
- `BillQueryRequest` 新增 `ledgerId` 字段（可选，用于按账本筛选账单）
- 创建账单时校验用户是否为该账本成员
- 查询账单时支持按 `ledgerId` 过滤

#### 统计按账本

- 统计接口新增可选参数 `ledgerId`，传入时只统计该账本下的账单
- 不传时统计用户所有账本（含共享账本中自己参与的）

#### Scenario: 用户注册自动创建默认账本
- **WHEN** 用户注册成功
- **THEN** 自动创建一个 type=1（个人）的默认账本，名称为"默认账本"
- **AND** 在 `ledger_member` 表中插入所有者记录（role=1）

#### Scenario: 创建共享账本并邀请成员
- **WHEN** 用户 A 创建共享账本并邀请用户 B
- **THEN** 用户 B 在自己的账本列表中能看到该账本
- **AND** 用户 B 可以在该账本下创建账单

#### Scenario: 按账本查询统计
- **WHEN** 用户请求 `/api/statistics/monthly?year=2026&month=1&ledgerId=5`
- **THEN** 只返回 ledger_id=5 下的账单统计

---

### Requirement: 主启动类命名对齐

Webflux 主启动类从 `AccountingApplication` 重命名为 `AccountingWebfluxApplication`，与 WebMVC 的 `AccountingWebmvcApplication` 对齐。

#### Scenario: 命名一致
- **WHEN** 查看两个后端的主启动类
- **THEN** Webflux 为 `AccountingWebfluxApplication.java`
- **AND** WebMVC 为 `AccountingWebmvcApplication.java`

---

### Requirement: 修复预设分类重复初始化

当前预设分类的 `user_id` 为 NULL，MySQL 唯一约束 `uk_user_name_type (user_id, name, type)` 对 NULL 值不生效（多个 NULL 视为不同值），导致每次启动都会重复插入预设分类。

**修复方案**：预设分类的 `user_id` 设为 0（而非 NULL），使唯一约束正常工作，配合 `ON DUPLICATE KEY UPDATE` 实现幂等初始化。

- `schema.sql` 中 `category` 表的 `user_id` 注释改为 `用户ID（预设分类为0）`
- `data.sql` 中预设分类 INSERT 语句显式设置 `user_id = 0`
- `CategoryService` 中查询预设分类的条件从 `is_preset = 1` 改为 `is_preset = 1 OR user_id = 0`（或保持 `is_preset = 1` 不变，因为查询逻辑基于 `is_preset` 标志）
- `BillService.createBill()` 中分类权限校验从 `!userId.equals(category.getUserId()) && !Integer.valueOf(1).equals(category.getIsPreset())` 保持不变（基于 `isPreset` 判断）

#### Scenario: 多次启动不产生重复预设分类
- **WHEN** 服务多次启动执行 `data.sql`
- **THEN** 预设分类只有 12 条，不会重复插入
- **AND** `ON DUPLICATE KEY UPDATE` 触发更新而非插入

#### Scenario: 预设分类 user_id 为 0
- **WHEN** 查询预设分类数据
- **THEN** 所有预设分类的 `user_id` 为 0（不是 NULL）

---

### Requirement: 移除 salt 字段

BCrypt 自带盐值机制，`user` 表中的 `salt` 字段冗余。

- 从 `schema.sql` 中移除 `salt` 列定义
- 从 `data.sql` 中移除 `salt` 值
- 从 `User` 实体中移除 `salt` 字段
- 从 `AuthService.register()` 中移除 `.salt("")` 调用

#### Scenario: 注册不再设置 salt
- **WHEN** 用户注册
- **THEN** `user` 表中 salt 列为 NULL
- **AND** BCrypt 密码验证正常工作

---

### Requirement: MongoDB 与 MySQL 账单明细一致性

确保 `BillDocument`（MongoDB）与 `Bill`（MySQL）在增删改时保持一致。

#### 修复点

**Webflux `updateBill`**:
- 当 `billDocumentRepository.findByMysqlId()` 返回空时，补建 MongoDB 文档（而非静默跳过）
- 改为：`switchIfEmpty(Mono.defer(() -> Mono.just(新建 BillDocument)))` 后再 save

**WebMVC `updateBill`**:
- 当 `billDocumentRepository.findByMysqlId()` 返回空时，补建 MongoDB 文档
- 改为：`if (document == null) { document = new BillDocument(...); }` 后再 save

**Webflux `deleteBill`**:
- `findByMysqlId` 返回空时，`flatMap` 不执行但 `.then()` 正常完成 — 当前行为正确，保持不变

**WebMVC `deleteBill`**:
- `deleteByMysqlId` 即使文档不存在也不报错 — 当前行为正确，保持不变

#### Scenario: MySQL 存在但 MongoDB 缺失时更新账单
- **WHEN** 更新一条 MySQL 中存在但 MongoDB 中不存在的账单
- **THEN** MySQL 正常更新
- **AND** MongoDB 中自动补建对应文档，字段与 MySQL 一致

## MODIFIED Requirements

### Requirement: BillRequest / BillQueryRequest 新增 ledgerId
`BillRequest` 新增 `private Long ledgerId;`（可选字段），`BillQueryRequest` 新增 `private Long ledgerId;`（可选字段）。

### Requirement: 统计接口新增 ledgerId 参数
周/月/年/分类统计接口均新增可选 `ledgerId` 参数，SQL 查询条件中增加 `AND ledger_id = :ledgerId`（当参数存在时）。

## REMOVED Requirements

### Requirement: user 表 salt 字段
**Reason**: BCrypt 自带盐值，无需额外存储 salt
**Migration**: 从 schema.sql 和 User 实体中移除 salt 字段，已有数据的 salt 列保留为 NULL（或通过 ALTER TABLE 删除列）
