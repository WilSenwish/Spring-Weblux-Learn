# MongoDB 设计文档

## 一、设计背景

记账系统采用 **MySQL + MongoDB** 混合存储架构：
- **MySQL**：存储结构化数据（用户、分类、账单主表），支持复杂查询和事务
- **MongoDB**：存储原始记账记录明细，用于审计追溯、历史数据归档、大数据量下的灵活查询

为什么用 MongoDB 存原始明细：
1. **写性能好**：记账操作是典型的写多读少场景，MongoDB 的文档写入性能优异
2. **Schema 灵活**：未来如需扩展记账字段（如图片、标签、地理位置等），无需修改表结构
3. **水平扩展**：数据量增长后可通过分片轻松扩展
4. **审计追溯**：保留每一次操作的完整快照，便于历史回溯

## 二、文档结构

### 集合名称

`bill_records`

### 文档字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| _id | ObjectId / String | 是 | MongoDB 主键 |
| mysqlId | Long | 是 | 对应 MySQL bill 表的主键 ID，用于关联 |
| userId | Long | 是 | 用户 ID |
| categoryId | Long | 是 | 分类 ID |
| amount | Decimal128 / BigDecimal | 是 | 金额 |
| type | Integer | 是 | 1-收入 2-支出 |
| remark | String | 否 | 备注 |
| billDate | Date | 是 | 账单日期 |
| createdAt | Date | 是 | 创建时间 |
| updatedAt | Date | 是 | 更新时间 |

### 文档示例

```json
{
  "_id": "675a1b3c2d8f4a1b2c3d4e5f",
  "mysqlId": 1001,
  "userId": 1,
  "categoryId": 5,
  "amount": { "$numberDecimal": "35.50" },
  "type": 2,
  "remark": "午餐-黄焖鸡米饭",
  "billDate": ISODate("2026-01-15T00:00:00Z"),
  "createdAt": ISODate("2026-01-15T10:30:00Z"),
  "updatedAt": ISODate("2026-01-15T10:30:00Z")
}
```

### BillDocument 实体定义

项目中的 MongoDB 实体类（两套后端共用相同字段）：

```java
@Document(collection = "bill_document")
public class BillDocument {
    @Id private String id;              // MongoDB ObjectId
    private Long mysqlId;               // 关联 MySQL bill.id
    private Long userId;                // 用户 ID
    private Long categoryId;            // 分类 ID
    private Long ledgerId;              // 账本 ID（新增）
    private BigDecimal amount;          // 金额
    private Integer type;               // 1-收入 2-支出
    private String remark;              // 备注
    private LocalDate billDate;         // 账单日期
    private LocalDateTime createdAt;    // 创建时间
    private LocalDateTime updatedAt;    // 更新时间
}
```

### MySQL bill 与 MongoDB bill_document 字段映射

| MongoDB 字段 | MySQL 字段 | 类型 | 说明 |
|---|---|---|---|
| _id | - | String | MongoDB 主键 |
| mysqlId | id | Long | 关联 MySQL bill.id |
| userId | user_id | Long | 用户 ID |
| categoryId | category_id | Long | 分类 ID |
| ledgerId | ledger_id | Long | 账本 ID |
| amount | amount | BigDecimal/DECIMAL(10,2) | 金额 |
| type | type | Integer/TINYINT | 1-收入 2-支出 |
| remark | remark | String/VARCHAR(255) | 备注 |
| billDate | bill_date | LocalDate/DATE | 账单日期 |
| createdAt | created_at | LocalDateTime/DATETIME | 创建时间 |
| updatedAt | updated_at | LocalDateTime/DATETIME | 更新时间 |

## 三、索引设计

| 索引名 | 字段 | 类型 | 用途 |
|--------|------|------|------|
| idx_mysql_id | mysqlId | 单字段唯一索引 | 根据 MySQL ID 快速查找对应文档（修改/删除时用） |
| idx_user_date | { userId: 1, billDate: -1 } | 复合索引 | 按用户+日期范围查询明细 |
| idx_user_category | { userId: 1, categoryId: 1 } | 复合索引 | 按用户+分类统计 |

> 建议根据实际查询模式建立索引，避免过多索引影响写入性能。

## 四、同步机制

两套后端（Webflux 和 Web MVC）在操作账单时都会同步更新 MongoDB：

### 4.1 新增账单

```
MySQL 插入 bill 记录
    ↓ 成功后
MongoDB 插入 bill_records 文档（mysqlId = 新插入的 bill.id）
```

### 4.2 修改账单

```
MySQL 更新 bill 记录
    ↓ 成功后
MongoDB 根据 mysqlId 查找文档并更新字段
```

### 4.3 删除账单

```
MySQL 删除 bill 记录
    ↓ 成功后
MongoDB 根据 mysqlId 查找文档并删除
```

### 4.4 一致性说明

- 当前采用**先 MySQL 后 MongoDB** 的顺序，无分布式事务保证
- 如果 MySQL 成功但 MongoDB 失败，会抛出异常，上层返回失败
- 极端情况下可能出现 MySQL 有数据但 MongoDB 无数据的情况（非事务性）
- 如需强一致性，可考虑引入分布式事务方案（如 Seata）或使用 Change Data Capture（CDC）方案

### 4.5 启动时 MongoDB 同步机制

为修复极端情况下 MySQL 与 MongoDB 数据不一致的问题，项目提供 `MongoSyncRunner` 启动任务：

- Webflux 版：实现 `ApplicationRunner`
- WebMVC 版：实现 `CommandLineRunner`

启动时执行步骤：
1. 查询 MySQL `bill` 表所有记录
2. 查询 MongoDB `bill_document` 集合所有 `mysqlId`
3. 对比找出缺失的 mysqlId（即 MySQL 存在但 MongoDB 不存在的记录）
4. 为缺失记录补建 `BillDocument` 并调用 `save` 写入 MongoDB

该机制保证每次应用启动时自动补偿历史遗漏的 MongoDB 文档，无需人工介入。

## 五、两套后端的一致性

| 项 | Webflux 版 | Web MVC 版 |
|----|-----------|-----------|
| 集合名 | bill_records | bill_records |
| 文档字段 | 完全一致 | 完全一致 |
| 关联字段 | mysqlId | mysqlId |
| 同步时机 | 新增/修改/删除时 | 新增/修改/删除时 |
| 驱动方式 | ReactiveMongoRepository（响应式） | MongoRepository（阻塞式） |

两套后端可以同时操作同一个 MongoDB 集合，数据完全互通。

## 六、常用查询示例

### 按用户+日期范围查询明细

```javascript
db.bill_records.find({
  userId: 1,
  billDate: {
    $gte: ISODate("2026-01-01"),
    $lte: ISODate("2026-01-31")
  }
}).sort({ billDate: -1 })
```

### 按分类统计金额

```javascript
db.bill_records.aggregate([
  { $match: { userId: 1, billDate: { $gte: ..., $lte: ... } } },
  { $group: {
      _id: "$categoryId",
      totalAmount: { $sum: "$amount" }
  }}
])
```

### 查找某日的所有记账

```javascript
db.bill_records.find({
  userId: 1,
  billDate: ISODate("2026-01-15")
})
```

## 七、数据量与性能估算

假设单用户每天记 10 笔账：
- 单用户年数据量：3650 条
- 10 万用户年数据量：3.65 亿条
- 单条文档约 200 字节 → 年存储约 70 GB

MongoDB 在单集合数十亿文档级别下仍能保持良好性能，必要时可通过以下方式扩展：
1. **分片**：按 userId 或 billDate 分片
2. **归档**：历史数据归档到冷存储
3. **索引优化**：根据查询模式建立合适索引

## 八、BillDocument 完整字段定义（最新）

```javascript
{
    _id:        ObjectId,           // MongoDB 主键
    mysqlId:    Number,             // 关联 MySQL bill.id（唯一索引建议）
    userId:     Number,             // 账单所属用户
    categoryId: Number,             // 分类ID
    ledgerId:   Number,             // 账本ID（与 MySQL bill.ledger_id 对齐）
    amount:     Number,             // 金额
    type:       Number,             // 1-收入 2-支出
    remark:     String,             // 备注
    billDate:   ISODate,            // 账单日期
    createdAt:  ISODate,            // 创建时间
    updatedAt:  ISODate             // 更新时间
}
```

## 九、MySQL bill 与 MongoDB bill_document 字段映射

| MySQL 字段 | MongoDB 字段 | 类型映射 | 说明 |
| --- | --- | --- | --- |
| `bill.id` | `mysqlId` | BIGINT → Number | **外键映射，业务主键** |
| `bill.user_id` | `userId` | BIGINT → Number | 用户ID |
| `bill.category_id` | `categoryId` | BIGINT → Number | 分类ID |
| `bill.ledger_id` | `ledgerId` | BIGINT → Number | 账本ID（账本功能新增字段） |
| `bill.amount` | `amount` | DECIMAL(10,2) → Number | 金额 |
| `bill.type` | `type` | TINYINT → Number | 1-收入 2-支出 |
| `bill.remark` | `remark` | VARCHAR(255) → String | 备注 |
| `bill.bill_date` | `billDate` | DATE → ISODate | 账单日期 |
| `bill.created_at` | `createdAt` | DATETIME → ISODate | 创建时间 |
| `bill.updated_at` | `updatedAt` | DATETIME → ISODate | 更新时间 |

## 十、启动时 MongoDB 同步机制

应用启动时，`MongoSyncRunner`（实现 `ApplicationRunner`）会自动扫描 MySQL `bill` 表中无对应 MongoDB 文档的记录，**补写 MongoDB 文档**。该机制在两套后端（Webflux 8080 / WebMVC 8081）独立运行：

- **触发时机**：应用启动完成后立即执行（`run` 方法在 Spring 上下文就绪后被调用）
- **同步策略**：以 MySQL 为准，遍历 `bill` 表，查询 MongoDB `bill_document` 中是否存在 `mysqlId == bill.id` 的文档；不存在则构建 `BillDocument` 并 `save` 写入
- **删除策略**：**仅写不删** —— MongoDB 中存在的"孤儿"（MySQL 已删除但 MongoDB 残留）需要手动清理；本项目假设业务流 create/update/delete 都会同步双写，MongoSyncRunner 只处理"启动时漏同步"的情况
- **典型场景**：后端宕机期间产生的账单 → 启动后自动补齐；MongoDB 重置后 → 启动后从 MySQL 重建
- **代码位置**：
  - Webflux: `accounting-webflux-backend/src/main/java/com/example/accounting/config/MongoSyncRunner.java`
  - WebMVC: `accounting-webmvc-backend/src/main/java/com/example/accounting/config/MongoSyncRunner.java`

## 十一、双数据源一致性保障

1. **业务流实时双写**：`BillService.createBill / updateBill / deleteBill` 在 MySQL 操作完成后立即同步 MongoDB，错误通过 `.onErrorResume` 传播
2. **启动时全量校验**：`MongoSyncRunner` 弥补业务流因异常中断未同步的记录
3. **手动对账命令**：使用 `mongosh` 或 `mysql` 客户端定期检查 `SELECT COUNT(*) FROM bill` 与 `db.bill_document.countDocuments()` 是否一致
