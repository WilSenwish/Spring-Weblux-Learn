# Tasks

- [x] Task 1: 主启动类命名对齐 + salt 字段移除 + 预设分类修复
  - [x] SubTask 1.1: Webflux 主启动类 `AccountingApplication.java` 重命名为 `AccountingWebfluxApplication.java`
  - [x] SubTask 1.2: `accounting-common` 中 `User` 实体移除 `salt` 字段
  - [x] SubTask 1.3: 双栈 `schema.sql` 移除 `salt` 列定义
  - [x] SubTask 1.4: 双栈 `data.sql` 移除 `salt` 值
  - [x] SubTask 1.5: 双栈 `AuthService.register()` 移除 `.salt("")` 调用
  - [x] SubTask 1.6: 双栈 `data.sql` 预设分类 INSERT 语句显式设置 `user_id = 0`
  - [x] SubTask 1.7: 双栈 `schema.sql` 中 `category` 表 `user_id` 注释改为 `用户ID（预设分类为0）`

- [x] Task 2: 新增账本相关实体和 DTO 到 `accounting-common`
  - [x] SubTask 2.1: 创建 `entity/Ledger.java`
  - [x] SubTask 2.2: 创建 `entity/LedgerMember.java`
  - [x] SubTask 2.3: `entity/Bill.java` 新增 `ledgerId` 字段
  - [x] SubTask 2.4: `entity/BillDocument.java` 新增 `ledgerId` 字段
  - [x] SubTask 2.5: 创建 `dto/LedgerRequest.java`
  - [x] SubTask 2.6: 创建 `dto/LedgerMemberRequest.java`
  - [x] SubTask 2.7: `dto/BillRequest.java` 新增 `ledgerId` 字段
  - [x] SubTask 2.8: `dto/BillQueryRequest.java` 新增 `ledgerId` 字段

- [x] Task 3: 数据库脚本更新
  - [x] SubTask 3.1: `schema.sql` 新增 `ledger` 表（含索引）
  - [x] SubTask 3.2: `schema.sql` 新增 `ledger_member` 表（含索引）
  - [x] SubTask 3.3: `schema.sql` 的 `bill` 表新增 `ledger_id` 列和索引
  - [x] SubTask 3.4: `data.sql` 为测试用户插入默认账本数据
  - [x] SubTask 3.5: `data.sql` 为测试账单数据补充 `ledger_id`

- [x] Task 4: Webflux 后端 — 账本功能实现
  - [x] SubTask 4.1: 创建 `LedgerRepository`、`LedgerMemberRepository`（R2DBC）
  - [x] SubTask 4.2: 创建 `LedgerService`（创建/查询/更新/删除账本，成员管理，注册时自动创建默认账本）
  - [x] SubTask 4.3: 创建 `LedgerController`（8 个接口）
  - [x] SubTask 4.4: `AuthService.register()` 注册成功后自动创建默认个人账本
  - [x] SubTask 4.5: `BillService.createBill()` 增加账本成员校验，设置 `ledgerId`
  - [x] SubTask 4.6: `BillService.listBills()` 支持 `ledgerId` 过滤
  - [x] SubTask 4.7: `StatisticsService` 四个统计方法增加 `ledgerId` 参数支持

- [x] Task 5: WebMVC 后端 — 账本功能实现
  - [x] SubTask 5.1: 创建 `LedgerMapper`、`LedgerMemberMapper`（MyBatis）和对应 XML
  - [x] SubTask 5.2: 创建 `LedgerService`（创建/查询/更新/删除账本，成员管理，注册时自动创建默认账本）
  - [x] SubTask 5.3: 创建 `LedgerController`（8 个接口）
  - [x] SubTask 5.4: `AuthService.register()` 注册成功后自动创建默认个人账本
  - [x] SubTask 5.5: `BillService.createBill()` 增加账本成员校验，设置 `ledgerId`
  - [x] SubTask 5.6: `BillService.listBills()` 支持 `ledgerId` 过滤
  - [x] SubTask 5.7: `StatisticsMapper` 和 `StatisticsService` 增加 `ledgerId` 参数支持

- [x] Task 6: 修复 MongoDB 与 MySQL 账单一致性
  - [x] SubTask 6.1: Webflux `BillService.updateBill()` — MongoDB 文档不存在时补建
  - [x] SubTask 6.2: WebMVC `BillService.updateBill()` — MongoDB 文档不存在时补建

- [x] Task 7: 编译验证与接口测试
  - [x] SubTask 7.1: 执行 `mvn clean compile` 确保全项目编译通过
  - [x] SubTask 7.2: 启动双栈后端服务（8080/8081）
  - [x] SubTask 7.3: 测试账本 CRUD 接口（双栈）
  - [x] SubTask 7.4: 测试账本成员管理接口（双栈）
  - [x] SubTask 7.5: 测试带 `ledgerId` 的账单创建和查询（双栈）
  - [x] SubTask 7.6: 测试带 `ledgerId` 的统计接口（双栈）

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4, Task 5, Task 6 can run in parallel after Task 3
- Task 7 depends on Task 4, Task 5, Task 6
