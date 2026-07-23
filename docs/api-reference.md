# 双栈记账系统 - API 参考文档

## 概述

- 基础路径：`/api`
- 认证方式：Bearer Token（JWT）
- 响应格式：统一使用 `ApiResponse<T>` 封装

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

---

## 一、用户认证模块

### 1.1 用户注册

- **接口**：`POST /api/auth/register`
- **是否需要认证**：否

**请求体**：
```json
{
  "username": "testuser",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名（唯一） |
| password | string | 是 | 密码（至少 6 位） |

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "testuser",
    "createdAt": "2026-01-01T00:00:00"
  }
}
```

### 1.2 用户登录

- **接口**：`POST /api/auth/login`
- **是否需要认证**：否

**请求体**：
```json
{
  "username": "testuser",
  "password": "123456"
}
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400000
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| token | string | JWT Token |
| expiresIn | number | 过期时间（毫秒，24小时） |

### 1.3 刷新 Token

- **接口**：`POST /api/auth/refresh`
- **是否需要认证**：是

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

## 二、分类管理模块

### 2.1 获取分类列表

- **接口**：`GET /api/categories`
- **是否需要认证**：是

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | int | 否 | 1-收入 2-支出（不传则返回所有） |

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "userId": null,
      "name": "工资",
      "type": 1,
      "isPreset": 1,
      "createdAt": "2026-01-01T00:00:00"
    }
  ]
}
```

> 说明：返回用户自定义分类 + 系统预设分类（userId 为 null 表示预设）。

### 2.2 新增分类

- **接口**：`POST /api/categories`
- **是否需要认证**：是

**请求体**：
```json
{
  "name": "投资理财",
  "type": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 分类名称 |
| type | int | 是 | 1-收入 2-支出 |

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 100,
    "userId": 1,
    "name": "投资理财",
    "type": 1,
    "isPreset": 0,
    "createdAt": "2026-01-01T00:00:00"
  }
}
```

### 2.3 修改分类

- **接口**：`PUT /api/categories/{id}`
- **是否需要认证**：是

**请求体**：
```json
{
  "name": "理财收益",
  "type": 1
}
```

**响应**：同新增分类

### 2.4 删除分类

- **接口**：`DELETE /api/categories/{id}`
- **是否需要认证**：是

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

> 注意：预设分类（isPreset=1）不可删除。

---

## 三、账单管理模块

### 3.1 获取账单列表（分页）

- **接口**：`GET /api/bills`
- **是否需要认证**：是

**查询参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 1 | 页码 |
| size | int | 否 | 10 | 每页条数 |
| type | int | 否 | - | 1-收入 2-支出 |
| categoryId | long | 否 | - | 分类 ID |
| startDate | string | 否 | - | 开始日期（yyyy-MM-dd） |
| endDate | string | 否 | - | 结束日期（yyyy-MM-dd） |

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "userId": 1,
        "categoryId": 1,
        "amount": 10000.00,
        "type": 1,
        "remark": "1月工资",
        "billDate": "2026-01-15",
        "createdAt": "2026-01-15T10:00:00",
        "updatedAt": "2026-01-15T10:00:00"
      }
    ],
    "total": 100,
    "page": 1,
    "size": 10
  }
}
```

### 3.2 新增账单

- **接口**：`POST /api/bills`
- **是否需要认证**：是

**请求体**：
```json
{
  "categoryId": 5,
  "amount": 35.50,
  "type": 2,
  "remark": "午餐",
  "billDate": "2026-01-15"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| categoryId | long | 是 | 分类 ID |
| amount | decimal | 是 | 金额 |
| type | int | 是 | 1-收入 2-支出 |
| remark | string | 否 | 备注 |
| billDate | string | 是 | 账单日期（yyyy-MM-dd） |

**响应**：同账单详情

### 3.3 修改账单

- **接口**：`PUT /api/bills/{id}`
- **是否需要认证**：是

**请求体**：同新增账单

**响应**：同账单详情

### 3.4 删除账单

- **接口**：`DELETE /api/bills/{id}`
- **是否需要认证**：是

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 四、账本管理模块

### 4.1 创建账本

- **接口**：`POST /api/ledgers`
- **是否需要认证**：是

**请求体**：
```json
{
  "name": "家庭账本",
  "description": "家庭共享账本",
  "type": 2,
  "allowMemberEdit": 0
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 账本名称 |
| description | string | 否 | 账本描述 |
| type | int | 是 | 账本类型（如 1-个人 2-家庭 3-团队） |
| allowMemberEdit | int | 否 | 是否允许成员编辑他人账单（0-否 1-是，默认 1） |

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "家庭账本",
    "description": "家庭共享账本",
    "ownerId": 1,
    "type": 2,
    "allowMemberEdit": 0,
    "createdAt": "2026-01-15T10:00:00",
    "updatedAt": "2026-01-15T10:00:00"
  }
}
```

### 4.2 查询当前用户可见的所有账本

- **接口**：`GET /api/ledgers`
- **是否需要认证**：是

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "家庭账本",
      "description": "家庭共享账本",
      "ownerId": 1,
      "type": 2,
      "allowMemberEdit": 0,
      "createdAt": "2026-01-15T10:00:00",
      "updatedAt": "2026-01-15T10:00:00"
    }
  ]
}
```

> 说明：返回当前用户作为成员（含所有者）的所有账本。

### 4.3 查询账本详情

- **接口**：`GET /api/ledgers/{id}`
- **是否需要认证**：是

**响应**：同创建账本响应。

### 4.4 更新账本

- **接口**：`PUT /api/ledgers/{id}`
- **是否需要认证**：是

**请求体**：
```json
{
  "name": "家庭账本（更新）",
  "description": "家庭共享账本",
  "type": 2,
  "allowMemberEdit": 1
}
```

**响应**：同创建账本响应。

> 权限说明：仅账本所有者或管理员可更新账本。

### 4.5 删除账本

- **接口**：`DELETE /api/ledgers/{id}`
- **是否需要认证**：是

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

> 权限说明：仅账本所有者可删除账本。

### 4.6 查询账本成员列表

- **接口**：`GET /api/ledgers/{id}/members`
- **是否需要认证**：是

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "userId": 1,
      "username": "owner",
      "role": 1,
      "joinedAt": "2026-01-15T10:00:00"
    },
    {
      "userId": 2,
      "username": "member",
      "role": 3,
      "joinedAt": "2026-01-16T10:00:00"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | long | 用户 ID |
| username | string | 用户名 |
| role | int | 角色（1-所有者 2-管理员 3-普通成员） |
| joinedAt | string | 加入时间 |

### 4.7 邀请成员加入账本

- **接口**：`POST /api/ledgers/{id}/members`
- **是否需要认证**：是

**请求体**：
```json
{
  "userId": 2,
  "role": 3
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | long | 是 | 被邀请用户 ID |
| role | int | 是 | 角色（1-所有者 2-管理员 3-普通成员） |

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 2,
    "role": 3,
    "joinedAt": "2026-01-16T10:00:00"
  }
}
```

### 4.8 移除成员

- **接口**：`DELETE /api/ledgers/{id}/members/{userId}`
- **是否需要认证**：是

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

> 权限说明：仅账本所有者或管理员可移除成员。

---

## 五、字段更新说明

本节集中记录 Request/Response 的字段更新，新增字段均向后兼容。

### 5.1 BillRequest

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| categoryId | long | 是 | 分类 ID |
| amount | decimal | 是 | 金额 |
| type | int | 是 | 1-收入 2-支出 |
| remark | string | 否 | 备注 |
| billDate | string | 是 | 账单日期（yyyy-MM-dd） |
| **ledgerId** | **Long** | **否** | **账本 ID，记账时使用** |

### 5.2 CategoryRequest

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 分类名称 |
| type | int | 是 | 1-收入 2-支出 |
| **ledgerId** | **Long** | **否** | **账本 ID，自定义分类时关联账本（NULL 表示预设分类）** |

### 5.3 LedgerRequest

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 账本名称 |
| description | string | 否 | 账本描述 |
| type | int | 是 | 账本类型 |
| **allowMemberEdit** | **Integer** | **否** | **成员是否可修改他人账单（默认 1）** |

### 5.4 BillResponse / Bill（实体）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 账单 ID |
| userId | long | 用户 ID |
| categoryId | long | 分类 ID |
| **ledgerId** | **Long** | **账本 ID** |
| amount | decimal | 金额 |
| type | int | 1-收入 2-支出 |
| remark | string | 备注 |
| billDate | string | 账单日期（yyyy-MM-dd） |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

### 5.5 LedgerResponse / Ledger（实体）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 账本 ID |
| name | string | 账本名称 |
| description | string | 账本描述 |
| ownerId | long | 所有者用户 ID |
| type | int | 账本类型 |
| **allowMemberEdit** | **Integer** | **成员权限开关** |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

---

## 六、统计分析模块

### 6.1 周统计

- **接口**：`GET /api/statistics/weekly`
- **是否需要认证**：是

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| date | string | 否 | 参考日期（yyyy-MM-dd），默认今天 |

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalIncome": 5000.00,
    "totalExpense": 2000.00,
    "details": [
      { "period": "2026-01-12", "income": 0, "expense": 100.00 },
      { "period": "2026-01-13", "income": 5000.00, "expense": 500.00 },
      ...
    ]
  }
}
```

> details 包含当前周 7 天数据，无数据的日期补零。

### 6.2 月统计

- **接口**：`GET /api/statistics/monthly`
- **是否需要认证**：是

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| date | string | 否 | 参考日期（yyyy-MM-dd），默认当月 |

**响应**：结构同周统计，details 为当月每天的数据（30/31 天）

### 4.3 年统计

- **接口**：`GET /api/statistics/yearly`
- **是否需要认证**：是

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| date | string | 否 | 参考日期（yyyy-MM-dd），默认当年 |

**响应**：结构同周统计，details 为当年 12 个月的数据（period 格式：yyyy-MM）

### 4.4 分类统计

- **接口**：`GET /api/statistics/category`
- **是否需要认证**：是

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | int | 否 | 1-收入 2-支出（不传则两者都返回） |
| startDate | string | 否 | 开始日期 |
| endDate | string | 否 | 结束日期 |

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalIncome": 10000.00,
    "totalExpense": 5000.00,
    "categories": [
      {
        "categoryId": 1,
        "categoryName": "工资",
        "type": 1,
        "amount": 8000.00,
        "percentage": 80.00
      },
      {
        "categoryId": 5,
        "categoryName": "餐饮",
        "type": 2,
        "amount": 2000.00,
        "percentage": 40.00
      }
    ]
  }
}
```

---

## 七、错误码说明

| 状态码 | code | message | 说明 |
|--------|------|---------|------|
| 401 | 401 | 未认证 / Token 无效 | 需要登录 |
| 403 | 403 | 无权限 | 无权操作该资源 |
| 400 | 400 | 请求参数错误 | 参数校验失败 |
| 500 | 500 | 系统内部错误 | 服务器异常 |
| 200 | -1 | 业务异常信息 | 具体业务错误（如分类不存在、预设分类不可删除等） |

---

## 八、账本管理接口补充

### 8.1 /api/ledgers 接口列表

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/ledgers` | 是 | 创建账本，请求体 `{name, description, type}`，type=1 个人 2 共享 |
| GET | `/api/ledgers` | 是 | 查询当前用户参与的所有账本 |
| GET | `/api/ledgers/{id}` | 是 | 查询账本详情（仅成员可见） |
| PUT | `/api/ledgers/{id}` | 是 | 修改账本（仅所有者/管理员可操作） |
| DELETE | `/api/ledgers/{id}` | 是 | 删除账本（仅所有者） |
| PUT | `/api/ledgers/{id}/allow-member-edit` | 是 | 切换 `allowMemberEdit`，仅所有者可操作 |
| GET | `/api/ledgers/{id}/members` | 是 | 查询账本成员列表 |
| POST | `/api/ledgers/{id}/members` | 是 | 邀请用户加入账本，请求体 `{userId, role}`，role=1 所有者 2 管理员 3 成员 |
| DELETE | `/api/ledgers/{id}/members/{userId}` | 是 | 移除成员（仅所有者/管理员） |

### 8.2 /api/bills 新增字段

请求体 `BillRequest` 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| **ledgerId** | **Long** | **否** | **账单所属账本，未指定时自动使用当前用户默认个人账本，无账本时返回 400** |

### 8.3 /api/categories 新增字段

请求体 `CategoryRequest` 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| **ledgerId** | **Long** | **否** | **自定义分类必须绑定账本（preset 分类 `ledgerId=null` 表示全局可见）** |

### 8.4 /api/ledgers 请求体字段

`LedgerRequest` 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 账本名称 |
| description | String | 否 | 账本描述 |
| type | Integer | 是 | 账本类型（1-个人 2-共享） |
| **allowMemberEdit** | **Boolean** | **否** | **控制成员是否可互相编辑账单（默认 true）** |
