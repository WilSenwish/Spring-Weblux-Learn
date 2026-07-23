# 数据库简表

本文档汇总项目所使用的 MySQL 表与 MongoDB 集合的建表（建集合）语句，仅供快速查阅。

## MySQL 表

### user

用户表，存储系统用户信息，登录认证依赖此表。

```sql
CREATE TABLE `user` (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL                COMMENT '用户名',
    password    VARCHAR(100) NOT NULL                COMMENT '加密密码(BCrypt)',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP                       COMMENT '创建时间',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

| 字段名     | 类型          | 是否必填 | 默认                            | 说明           |
|------------|---------------|----------|---------------------------------|----------------|
| id         | BIGINT        | 是       | AUTO_INCREMENT                  | 主键           |
| username   | VARCHAR(50)   | 是       | -                               | 用户名，唯一   |
| password   | VARCHAR(100)  | 是       | -                               | 加密密码       |
| created_at | DATETIME      | 否       | CURRENT_TIMESTAMP               | 创建时间       |
| updated_at | DATETIME      | 否       | CURRENT_TIMESTAMP 自动更新       | 更新时间       |

### category

分类表，存储账单分类（收入/支出），支持预设分类与用户自定义分类。

```sql
CREATE TABLE category (
    id         BIGINT      NOT NULL AUTO_INCREMENT                            COMMENT '主键',
    user_id    BIGINT      DEFAULT 0                                          COMMENT '用户ID（预设分类为0）',
    ledger_id  BIGINT      DEFAULT NULL                                       COMMENT '账本ID（NULL表示全局可见）',
    name       VARCHAR(50) NOT NULL                                           COMMENT '分类名称',
    type       TINYINT     NOT NULL                                           COMMENT '1-收入 2-支出',
    is_preset  TINYINT     DEFAULT 0                                          COMMENT '0-自定义 1-预设',
    created_at DATETIME    DEFAULT CURRENT_TIMESTAMP                           COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_ledger_name_type (user_id, ledger_id, name, type),
    INDEX idx_user_id (user_id),
    INDEX idx_ledger_id (ledger_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';
```

| 字段名     | 类型         | 是否必填 | 默认              | 说明                          |
|------------|--------------|----------|-------------------|-------------------------------|
| id         | BIGINT       | 是       | AUTO_INCREMENT    | 主键                          |
| user_id    | BIGINT       | 否       | 0                 | 用户ID（预设分类为0）          |
| ledger_id  | BIGINT       | 否       | NULL              | 账本ID（NULL 表示全局可见）     |
| name       | VARCHAR(50)  | 是       | -                 | 分类名称                      |
| type       | TINYINT      | 是       | -                 | 1-收入 2-支出                 |
| is_preset  | TINYINT      | 否       | 0                 | 0-自定义 1-预设               |
| created_at | DATETIME     | 否       | CURRENT_TIMESTAMP | 创建时间                      |

### ledger

账本表，记录用户创建的账本（个人/共享），用于账单归集。

```sql
CREATE TABLE ledger (
    id                BIGINT       NOT NULL AUTO_INCREMENT                            COMMENT '主键',
    name              VARCHAR(50)  NOT NULL                                           COMMENT '账本名称',
    description       VARCHAR(255) DEFAULT NULL                                       COMMENT '账本描述',
    owner_id          BIGINT       NOT NULL                                           COMMENT '创建者用户ID',
    type              TINYINT      NOT NULL DEFAULT 1                                 COMMENT '1-个人 2-共享',
    allow_member_edit TINYINT      DEFAULT 1                                          COMMENT '成员是否可修改他人账单（1-允许 0-仅改自己）',
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP                           COMMENT '创建时间',
    updated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_owner_id (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账本表';
```

| 字段名            | 类型          | 是否必填 | 默认                            | 说明                                |
|-------------------|---------------|----------|---------------------------------|-------------------------------------|
| id                | BIGINT        | 是       | AUTO_INCREMENT                  | 主键                                |
| name              | VARCHAR(50)   | 是       | -                               | 账本名称                            |
| description       | VARCHAR(255)  | 否       | NULL                            | 账本描述                            |
| owner_id          | BIGINT        | 是       | -                               | 创建者用户ID                        |
| type              | TINYINT       | 是       | 1                               | 1-个人 2-共享                       |
| allow_member_edit | TINYINT       | 否       | 1                               | 成员是否可修改他人账单              |
| created_at        | DATETIME      | 否       | CURRENT_TIMESTAMP               | 创建时间                            |
| updated_at        | DATETIME      | 否       | CURRENT_TIMESTAMP 自动更新       | 更新时间                            |

### ledger_member

账本成员表，账本与用户的多对多关系表，记录成员角色。

```sql
CREATE TABLE ledger_member (
    id        BIGINT   NOT NULL AUTO_INCREMENT                  COMMENT '主键',
    ledger_id BIGINT   NOT NULL                                 COMMENT '账本ID',
    user_id   BIGINT   NOT NULL                                 COMMENT '用户ID',
    role      TINYINT  NOT NULL DEFAULT 3                       COMMENT '1-所有者 2-管理员 3-普通成员',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP                COMMENT '加入时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_user (ledger_id, user_id),
    INDEX idx_ledger_id (ledger_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账本成员表';
```

| 字段名    | 类型     | 是否必填 | 默认              | 说明                      |
|-----------|----------|----------|-------------------|---------------------------|
| id        | BIGINT   | 是       | AUTO_INCREMENT    | 主键                      |
| ledger_id | BIGINT   | 是       | -                 | 账本ID                    |
| user_id   | BIGINT   | 是       | -                 | 用户ID                    |
| role      | TINYINT  | 是       | 3                 | 1-所有者 2-管理员 3-普通成员 |
| joined_at | DATETIME | 否       | CURRENT_TIMESTAMP | 加入时间                  |

### bill

账单表，核心业务表，记录每一笔收入/支出。

```sql
CREATE TABLE bill (
    id         BIGINT        NOT NULL AUTO_INCREMENT                            COMMENT '主键',
    user_id    BIGINT        NOT NULL                                           COMMENT '用户ID',
    category_id BIGINT       NOT NULL                                           COMMENT '分类ID',
    ledger_id  BIGINT        DEFAULT NULL                                       COMMENT '账本ID',
    amount     DECIMAL(10,2) NOT NULL                                           COMMENT '金额',
    type       TINYINT       NOT NULL                                           COMMENT '1-收入 2-支出',
    remark     VARCHAR(255)  DEFAULT NULL                                       COMMENT '备注',
    bill_date  DATE          NOT NULL                                           COMMENT '账单日期',
    created_at DATETIME      DEFAULT CURRENT_TIMESTAMP                           COMMENT '创建时间',
    updated_at DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_user_date (user_id, bill_date),
    INDEX idx_user_category (user_id, category_id),
    INDEX idx_user_type (user_id, type),
    INDEX idx_ledger_id (ledger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单表';
```

| 字段名      | 类型           | 是否必填 | 默认                            | 说明       |
|-------------|----------------|----------|---------------------------------|------------|
| id          | BIGINT         | 是       | AUTO_INCREMENT                  | 主键       |
| user_id     | BIGINT         | 是       | -                               | 用户ID     |
| category_id | BIGINT         | 是       | -                               | 分类ID     |
| ledger_id   | BIGINT         | 否       | NULL                            | 账本ID     |
| amount      | DECIMAL(10,2)  | 是       | -                               | 金额       |
| type        | TINYINT        | 是       | -                               | 1-收入 2-支出 |
| remark      | VARCHAR(255)   | 否       | NULL                            | 备注       |
| bill_date   | DATE           | 是       | -                               | 账单日期   |
| created_at  | DATETIME       | 否       | CURRENT_TIMESTAMP               | 创建时间   |
| updated_at  | DATETIME       | 否       | CURRENT_TIMESTAMP 自动更新       | 更新时间   |

## MongoDB 集合

### bill_document

账单文档集合，存储账单详情以支持灵活的查询与统计。`mysqlId` 关联 MySQL `bill.id`，主键为 MongoDB ObjectId。

```javascript
// MongoDB 集合（无需显式建集合，Spring Data 启动时会自动创建）
// 集合名：bill_document
// 文档结构示例：
{
    _id:        ObjectId,           // MongoDB 主键
    mysqlId:    Number,             // 关联 MySQL bill.id
    userId:     Number,             // 用户ID
    categoryId: Number,             // 分类ID
    ledgerId:   Number,             // 账本ID
    amount:     Number,             // 金额
    type:       Number,             // 1-收入 2-支出
    remark:     String,             // 备注
    billDate:   ISODate,            // 账单日期
    createdAt:  ISODate,            // 创建时间
    updatedAt:  ISODate             // 更新时间
}
```

| 字段名      | MongoDB 类型 | 是否必填 | 默认 | 说明                |
|-------------|--------------|----------|------|---------------------|
| _id         | ObjectId     | 是       | 自动 | MongoDB 主键         |
| mysqlId     | Number       | 是       | -    | 关联 MySQL bill.id   |
| userId      | Number       | 是       | -    | 用户ID              |
| categoryId  | Number       | 是       | -    | 分类ID              |
| ledgerId    | Number       | 否       | -    | 账本ID              |
| amount      | Number       | 是       | -    | 金额                |
| type        | Number       | 是       | -    | 1-收入 2-支出       |
| remark      | String       | 否       | -    | 备注                |
| billDate    | ISODate      | 是       | -    | 账单日期            |
| createdAt   | ISODate      | 否       | -    | 创建时间            |
| updatedAt   | ISODate      | 否       | -    | 更新时间            |
