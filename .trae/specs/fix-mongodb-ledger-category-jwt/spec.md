# MongoDB 同步 + 账本/分类增强 + JWT 公共提取 Spec

## Why
当前存在多个问题：1) data.sql 初始化的测试账单只写入 MySQL 未同步 MongoDB，导致 MongoDB 无 bill 数据；2) 用户未创建账本时记账会静默失败；3) 分类只与 user_id 关联，共享账本成员无法使用账本级别的自定义分类；4) 共享账本缺少成员间数据修改权限控制；5) JWT 工具类双栈重复实现，未提取到公共模块。

## What Changes
- 修复 data.sql 初始化数据未同步 MongoDB 的问题（改为启动后由 BillService 补偿同步，或 data.sql 初始化后由 schema.sql 的 MongoDB 初始化脚本补充）
- 账本创建前置校验：记账时无可用账本则抛异常
- **BREAKING**: `category` 表新增 `ledger_id` 字段，支持账本级别的自定义分类；`ledger` 表新增 `allow_member_edit` 开关字段
- JWT 工具类提取到 `accounting-common`：核心逻辑放公共模块，Webflux/MVC 各自仅做响应式/同步适配

## Impact
- 受影响模块: `accounting-common`、`accounting-webflux-backend`、`accounting-webmvc-backend`
- 受影响代码: `BillService`、`CategoryService`、`Ledger` 实体、`Category` 实体、`schema.sql`、`data.sql`、`JwtUtil`（双栈）

## ADDED Requirements

### Requirement: MongoDB 初始数据同步
data.sql 只能操作 MySQL，无法同步写入 MongoDB。需要在服务启动时补偿同步：若 MongoDB `bill_document` 集合中缺少对应 MySQL bill 记录，则自动补建。

#### Scenario: 首次启动时 MongoDB 自动同步 MySQL 账单
- **WHEN** 服务启动后，MySQL 中存在 bill 记录但 MongoDB `bill_document` 集合中无对应文档
- **THEN** 自动将缺失的 bill 记录同步到 MongoDB `bill_document` 集合

---

### Requirement: 记账前必须有账本
当前 `resolveLedgerId` 未指定账本时查找默认个人账本，如果用户没有任何账本则静默返回空（Mono empty），导致创建失败但没有明确错误信息。

#### Scenario: 用户无账本时记账报错
- **WHEN** 用户尝试创建账单但没有任何账本
- **THEN** 返回明确错误"请先创建账本"

#### Scenario: 未指定账本时自动使用默认个人账本
- **WHEN** 用户有默认个人账本且未指定 ledgerId
- **THEN** 自动使用该账本

---

### Requirement: 分类与账本绑定
当前自定义分类只与 `user_id` 关联。共享账本场景下，不同账本可能需要不同的自定义分类。`category` 表新增 `ledger_id` 字段：
- 预设分类（`is_preset=1`）：`ledger_id=NULL`，所有账本可见
- 自定义分类：`ledger_id` 关联到具体账本，仅在该账本下可见

#### Scenario: 预设分类所有账本可见
- **WHEN** 查询某账本的分类列表
- **THEN** 返回预设分类（`ledger_id IS NULL AND user_id=0`）+ 该账本的自定义分类（`ledger_id = ?`）

#### Scenario: 自定义分类绑定账本
- **WHEN** 用户在某账本下创建自定义分类
- **THEN** 该分类的 `ledger_id` 为该账本 ID
- **AND** 其他账本下不可见该分类

---

### Requirement: 账本成员数据修改权限控制
`ledger` 表新增 `allow_member_edit` 字段（TINYINT DEFAULT 1）：
- `1`：成员可互相修改对方在该账本下的账单（当前行为）
- `0`：成员只能修改自己创建的账单

#### Scenario: allow_member_edit=1 时成员可修改他人账单
- **WHEN** 账本的 `allow_member_edit=1` 且用户 A 和用户 B 同为该账本成员
- **THEN** 用户 A 可以修改用户 B 在该账本下的账单

#### Scenario: allow_member_edit=0 时成员只能修改自己的账单
- **WHEN** 账本的 `allow_member_edit=0`
- **THEN** 成员只能修改/删除自己创建的账单（`bill.user_id = 当前用户`）
- **AND** 所有者/管理员不受此限制

---

### Requirement: JWT 工具类提取到 accounting-common
双栈 `JwtUtil` 核心逻辑完全相同（生成/解析/验证 token），唯一区别是 Webflux 返回 `Mono` 包装。提取策略：
- `accounting-common` 中创建 `JwtUtil` 类，包含同步版本的 generate/extract/validate 方法
- Webflux `JwtUtil` 继承或委托公共类，添加 `Mono` 包装方法
- WebMVC `JwtUtil` 直接使用公共类

#### Scenario: JWT 核心逻辑不重复
- **WHEN** 查看双栈后端的 JwtUtil
- **THEN** 核心 token 生成/解析/验证逻辑定义在 `accounting-common` 中
- **AND** Webflux 版本仅添加响应式包装
- **AND** WebMVC 版本直接使用公共类

## MODIFIED Requirements

### Requirement: Ledger 实体新增 allowMemberEdit 字段
`entity/Ledger.java` 新增 `private Integer allowMemberEdit;`（默认值 1）。

### Requirement: Category 实体新增 ledgerId 字段
`entity/Category.java` 新增 `private Long ledgerId;`。

### Requirement: BillService 创建/更新/删除账单时校验账本成员权限
- 创建：当前已有校验，保持不变
- 更新/删除：检查 `allow_member_edit` 字段，若为 0 且非所有者/管理员，则只能操作自己的账单
