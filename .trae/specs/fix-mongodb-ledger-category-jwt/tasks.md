# Tasks

- [x] Task 1: MongoDB 初始数据同步
  - [x] SubTask 1.1: Webflux 后端新增启动后数据同步逻辑（ApplicationRunner/CommandLineRunner），查询 MySQL bill 中在 MongoDB 无对应文档的记录，补建 BillDocument
  - [x] SubTask 1.2: WebMVC 后端新增启动后数据同步逻辑（CommandLineRunner），同上
  - [x] SubTask 1.3: 验证重启后 MongoDB `bill_document` 集合数据与 MySQL `bill` 表一致

- [x] Task 2: 记账前置校验 — 无账本时报错
  - [x] SubTask 2.1: Webflux `BillService.resolveLedgerId()` — 未指定 ledgerId 且无默认账本时抛 `BusinessException("请先创建账本")`
  - [x] SubTask 2.2: WebMVC `BillService.createBill()` — 未指定 ledgerId 且无默认账本时抛 `BusinessException("请先创建账本")`

- [x] Task 3: 分类与账本绑定
  - [x] SubTask 3.1: `category` 表新增 `ledger_id BIGINT` 列和索引（双栈 schema.sql，幂等 ALTER）
  - [x] SubTask 3.2: `Category.java` 实体新增 `ledgerId` 字段
  - [x] SubTask 3.3: `CategoryRequest.java` DTO 新增 `ledgerId` 字段
  - [x] SubTask 3.4: Webflux `CategoryService.createCategory()` — 设置 `ledgerId`
  - [x] SubTask 3.5: WebMVC `CategoryService.createCategory()` — 设置 `ledgerId`
  - [x] SubTask 3.6: Webflux `CategoryService.listCategories()` — 改为接收 `ledgerId` 参数，返回预设分类 + 该账本的自定义分类
  - [x] SubTask 3.7: WebMVC `CategoryService.listCategories()` — 同上
  - [x] SubTask 3.8: Webflux `CategoryController.listCategories()` — 新增 `ledgerId` 参数
  - [x] SubTask 3.9: WebMVC `CategoryController.listCategories()` — 新增 `ledgerId` 参数
  - [x] SubTask 3.10: WebMVC `CategoryMapper.xml` — 修改查询 SQL 支持 `ledger_id` 过滤

- [x] Task 4: 账本成员数据修改权限控制
  - [x] SubTask 4.1: `ledger` 表新增 `allow_member_edit TINYINT DEFAULT 1` 列（双栈 schema.sql，幂等 ALTER）
  - [x] SubTask 4.2: `Ledger.java` 实体新增 `allowMemberEdit` 字段
  - [x] SubTask 4.3: `LedgerRequest.java` DTO 新增 `allowMemberEdit` 字段
  - [x] SubTask 4.4: Webflux `BillService.updateBill()` / `deleteBill()` — 检查账本 `allow_member_edit`，为 0 且非所有者/管理员时仅允许操作自己的账单
  - [x] SubTask 4.5: WebMVC `BillService.updateBill()` / `deleteBill()` — 同上
  - [x] SubTask 4.6: Webflux `LedgerService.updateLedger()` — 支持更新 `allowMemberEdit`
  - [x] SubTask 4.7: WebMVC `LedgerService.updateLedger()` — 支持更新 `allowMemberEdit`

- [x] Task 5: JWT 工具类提取到 accounting-common
  - [x] SubTask 5.1: `accounting-common` 新建 `security/JwtUtil.java`，包含同步版本的 generateToken/extractUsername/validateToken/isTokenExpired/getExpiration 方法
  - [x] SubTask 5.2: Webflux `JwtUtil` 改为继承公共类，保留 Mono 包装方法（委托超类同步方法）
  - [x] SubTask 5.3: WebMVC `JwtUtil` 改为继承公共类（或直接删除，SecurityConfig 注入公共类）
  - [x] SubTask 5.4: 双栈 SecurityConfig/SecurityFilterChain 中引用更新后的 JwtUtil

- [x] Task 6: 编译验证与接口测试
  - [x] SubTask 6.1: `mvn clean compile` 全项目编译通过
  - [x] SubTask 6.2: 启动双栈后端，验证 MongoDB 同步完成
  - [x] SubTask 6.3: 验证无账本时记账报错
  - [x] SubTask 6.4: 验证分类按账本过滤
  - [x] SubTask 6.5: 验证 `allow_member_edit` 权限控制

# Task Dependencies
- Task 3, Task 4 可并行执行
- Task 5 独立于 Task 2, 3, 4
- Task 2 独立
- Task 1 独立
- Task 6 depends on Task 1, 2, 3, 4, 5
