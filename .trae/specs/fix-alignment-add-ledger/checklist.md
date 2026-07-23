# Checklist

## 主启动类命名对齐
- [x] Webflux 主启动类文件名为 `AccountingWebfluxApplication.java`
- [x] Webflux 主启动类类名为 `AccountingWebfluxApplication`
- [x] WebMVC 主启动类文件名为 `AccountingWebmvcApplication.java`（已有，无需修改）

## salt 字段移除
- [x] `accounting-common` 的 `User.java` 不包含 `salt` 字段
- [x] 双栈 `schema.sql` 中 `user` 表不包含 `salt` 列
- [x] 双栈 `data.sql` 中 INSERT 语句不包含 `salt` 值
- [x] Webflux `AuthService.register()` 不包含 `.salt(...)` 调用
- [x] WebMVC `AuthService.register()` 不包含 `.salt(...)` 调用
- [x] 注册接口测试通过（密码验证正常）

## 预设分类重复初始化修复
- [x] 双栈 `data.sql` 预设分类 INSERT 语句显式设置 `user_id = 0`
- [x] 双栈 `schema.sql` 中 `category` 表 `user_id` 注释为 `用户ID（预设分类为0）`
- [x] 多次启动服务后预设分类仍只有 12 条（不重复插入）
- [x] 预设分类的 `user_id` 值为 0（不是 NULL）

## 账本实体和 DTO（accounting-common）
- [x] `entity/Ledger.java` 存在且包含 id, name, description, ownerId, type, createdAt, updatedAt
- [x] `entity/LedgerMember.java` 存在且包含 id, ledgerId, userId, role, joinedAt
- [x] `entity/Bill.java` 包含 `ledgerId` 字段
- [x] `entity/BillDocument.java` 包含 `ledgerId` 字段
- [x] `dto/LedgerRequest.java` 存在且包含 name, description, type
- [x] `dto/LedgerMemberRequest.java` 存在且包含 userId, role
- [x] `dto/BillRequest.java` 包含 `ledgerId` 字段
- [x] `dto/BillQueryRequest.java` 包含 `ledgerId` 字段

## 数据库脚本
- [x] `schema.sql` 包含 `ledger` 表建表语句（含 `idx_owner_id` 索引）
- [x] `schema.sql` 包含 `ledger_member` 表建表语句（含 `idx_ledger_id`、`idx_user_id`、`uk_ledger_user` 索引）
- [x] `schema.sql` 的 `bill` 表包含 `ledger_id` 列和 `idx_ledger_id` 索引
- [x] `data.sql` 为测试用户（admin、testuser）插入默认个人账本
- [x] `data.sql` 为测试账单数据补充 `ledger_id`

## Webflux 账本功能
- [x] `LedgerRepository` 和 `LedgerMemberRepository` 存在
- [x] `LedgerService` 实现 8 个方法（创建/列表/详情/更新/删除账本 + 成员列表/邀请/移除）
- [x] `LedgerController` 实现 8 个接口
- [x] `AuthService.register()` 注册后自动创建默认个人账本
- [x] `BillService.createBill()` 校验账本成员权限并设置 `ledgerId`
- [x] `BillService.listBills()` 支持 `ledgerId` 过滤
- [x] `StatisticsService` 四个统计方法支持 `ledgerId` 参数

## WebMVC 账本功能
- [x] `LedgerMapper` 和 `LedgerMemberMapper` 存在（含 XML）
- [x] `LedgerService` 实现 8 个方法
- [x] `LedgerController` 实现 8 个接口
- [x] `AuthService.register()` 注册后自动创建默认个人账本
- [x] `BillService.createBill()` 校验账本成员权限并设置 `ledgerId`
- [x] `BillService.listBills()` 支持 `ledgerId` 过滤
- [x] `StatisticsMapper` 和 `StatisticsService` 支持 `ledgerId` 参数

## MongoDB 一致性修复
- [x] Webflux `BillService.updateBill()` — MongoDB 文档不存在时补建
- [x] WebMVC `BillService.updateBill()` — MongoDB 文档不存在时补建

## 编译与运行验证
- [x] 根目录 `mvn clean compile` 全项目编译通过
- [x] Webflux 后端在 8080 端口正常启动
- [x] WebMVC 后端在 8081 端口正常启动
- [x] Webflux 注册接口正常（不设置 salt，自动创建默认账本）
- [x] WebMVC 注册接口正常（不设置 salt，自动创建默认账本）
- [x] Webflux 创建账本接口正常
- [x] WebMVC 创建账本接口正常
- [x] Webflux 邀请账本成员接口正常
- [x] WebMVC 邀请账本成员接口正常
- [x] Webflux 创建带 `ledgerId` 的账单正常
- [x] WebMVC 创建带 `ledgerId` 的账单正常
- [x] Webflux 按 `ledgerId` 查询账单正常
- [x] WebMVC 按 `ledgerId` 查询账单正常
- [x] Webflux 按 `ledgerId` 统计接口正常
- [x] WebMVC 按 `ledgerId` 统计接口正常
