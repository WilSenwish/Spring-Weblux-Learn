# 双栈后端代码重构 Spec

## Why
当前 Webflux 与 WebMVC 两个后端存在大量重复代码（DTO、Entity、ApiResponse、BusinessException、PageResult 等完全复制粘贴），维护成本高且容易不一致。同时 Service 层直接返回 `ApiResponse<T>`，导致业务代码与响应包装耦合。需要将公共代码抽取到独立 Maven 模块，并在响应层统一包装。

## What Changes
- 新建 `accounting-common` Maven 模块，抽取两个后端公共代码
- 双栈命名对齐（`document.BillDocument` → `entity.BillDocument`）
- Service 层返回类型去 `ApiResponse` 化，改为裸数据类型
- WebMVC 后端通过 `ResponseBodyAdvice` 统一包装响应
- Webflux 后端在 Controller 层通过 `.map(ApiResponse::ok)` 统一包装
- 全局异常处理器适配新返回类型
- **BREAKING**: 两个后端 pom.xml 增加对 `accounting-common` 的依赖；父 pom 增加子模块

## Impact
- 受影响模块: `accounting-webflux-backend`、`accounting-webmvc-backend`、根 `pom.xml`
- 新增模块: `accounting-common`
- 受影响代码: Service 层所有方法签名、Controller 层返回类型、GlobalExceptionHandler

## ADDED Requirements

### Requirement: 公共模块 accounting-common

`accounting-common` 作为纯 POJO/工具模块，不依赖任何 Spring Boot Starter，仅依赖 `lombok` 和 `spring-data-commons`（用于 `@Id`、`@Table`、`@Document` 注解）。

#### 抽取的公共类清单

| 类 | 原位置（双栈都有） | 说明 |
|---|---|---|
| `ApiResponse<T>` | `common.ApiResponse` | 统一响应包装体 |
| `PageResult<T>` | `common.PageResult` | 分页结果 |
| `BusinessException` | `common.BusinessException` | 业务异常 |
| `LoginRequest` | `dto.LoginRequest` | 登录请求 DTO |
| `RegisterRequest` | `dto.RegisterRequest` | 注册请求 DTO |
| `LoginResponse` | `dto.LoginResponse` | 登录响应 DTO |
| `BillRequest` | `dto.BillRequest` | 账单请求 DTO |
| `BillQueryRequest` | `dto.BillQueryRequest` | 账单查询 DTO |
| `User` | `entity.User` | 合并 R2DBC `@Table`/`@Id` 与 MyBatis 无注解版本，同时保留注解（MyBatis 不依赖它们） |
| `Bill` | `entity.Bill` | 同上 |
| `Category` | `entity.Category` | 同上 |
| `BillDocument` | `entity.BillDocument` | Webflux 原在 `document` 包，统一移到 `entity` 包；两个版本内容完全一致（`@Document` + `@Id`） |

#### 包结构
```
com.example.accounting.common
  ApiResponse.java
  PageResult.java
  BusinessException.java
com.example.accounting.dto
  LoginRequest.java
  RegisterRequest.java
  LoginResponse.java
  BillRequest.java
  BillQueryRequest.java
com.example.accounting.entity
  User.java
  Bill.java
  Category.java
  BillDocument.java
```

#### 依赖设计
- `groupId`: `com.example.accounting`
- `artifactId`: `accounting-common`
- `packaging`: `jar`
- 仅依赖:
  - `org.projectlombok:lombok` (provided)
  - `org.springframework.data:spring-data-commons` (provided，仅用于 `@Id`、`@Table`、`@Document`)
  - `jakarta.validation:jakarta.validation-api` (provided，用于 `@NotBlank`、`@Size`)

#### Scenario: Webflux 后端引用公共模块
- **WHEN** 编译 `accounting-webflux-backend`
- **THEN** 能从 `accounting-common` 正确引入 `ApiResponse`、`User`、`BillDocument` 等类
- **AND** 原有功能不受影响

#### Scenario: WebMVC 后端引用公共模块
- **WHEN** 编译 `accounting-webmvc-backend`
- **THEN** 能从 `accounting-common` 正确引入所有公共类
- **AND** 原有功能不受影响

---

### Requirement: Service 层返回类型去 ApiResponse 化

Service 层只关注业务逻辑，不直接构造 `ApiResponse`。返回裸数据类型，由调用方（Controller 或响应层切面）统一包装。

#### Webflux 后端
- 原签名: `Mono<ApiResponse<T>> method(...)`
- 新签名: `Mono<T> method(...)`
- 业务校验/异常统一通过 `Mono.error(new BusinessException(...))` 抛出
- 涉及 Service: `AuthService`、`BillService`、`CategoryService`、`StatisticsService`

#### WebMVC 后端
- 原签名: `ApiResponse<T> method(...)`
- 新签名: `T method(...)`
- 业务校验/异常统一通过 `throw new BusinessException(...)` 抛出
- 涉及 Service: `AuthService`、`BillService`、`CategoryService`、`StatisticsService`

#### Scenario: AuthService 改造
- **WHEN** 调用 `register(RegisterRequest)`
- **THEN** Webflux 返回 `Mono<String>`（成功消息），WebMVC 返回 `String`
- **AND** 用户名已存在时抛出 `BusinessException`，不再返回 `ApiResponse.error`

#### Scenario: BillService 改造
- **WHEN** 调用 `createBill(Long, BillRequest)`
- **THEN** Webflux 返回 `Mono<Bill>`，WebMVC 返回 `Bill`
- **AND** 分类不存在时抛出 `BusinessException`

---

### Requirement: 响应层统一包装

#### WebMVC 后端 — ResponseBodyAdvice
创建 `ApiResponseAdvice` 实现 `ResponseBodyAdvice<Object>`：
- **WHEN** Controller 方法返回非 `ApiResponse` 类型且非 `String` 类型
- **THEN** 自动包装为 `ApiResponse.ok(body)`
- **AND** 返回 `String` 时特殊处理（避免与默认 StringHttpMessageConverter 冲突，可返回 `ApiResponse.ok(body)`）
- **AND** 已经返回 `ApiResponse` 的类型不再二次包装

#### Webflux 后端 — Controller 层统一 map
Webflux 的 `ResponseBodyAdvice` 对 `Mono<T>` 的包装支持不如 WebMVC 直观，因此采用 Controller 层统一 `.map(ApiResponse::ok)`：
- **WHEN** Controller 调用 Service 方法
- **THEN** 在返回前追加 `.map(ApiResponse::ok)`
- **AND** 异常仍由 `GlobalExceptionHandler` 捕获并包装为 `ApiResponse.error`

#### Scenario: WebMVC 自动包装
- **WHEN** Controller 返回 `Bill` 对象
- **THEN** HTTP 响应体为 `{"code":200,"message":"success","data":{...}}`

#### Scenario: Webflux 手动包装
- **WHEN** Controller 返回 `billService.createBill(...).map(ApiResponse::ok)`
- **THEN** HTTP 响应体为 `{"code":200,"message":"success","data":{...}}`

---

### Requirement: 全局异常处理器适配

#### WebMVC GlobalExceptionHandler
- 各 `@ExceptionHandler` 方法返回 `ApiResponse<?>`（不再返回 `ResponseEntity<ApiResponse<?>>`）
- 由 `ApiResponseAdvice` 处理时已经包装好，无需再包 `ResponseEntity`
- 或者直接返回 `ApiResponse<?>`，`ApiResponseAdvice` 识别到已是 `ApiResponse` 则透传

#### Webflux GlobalExceptionHandler
- 各 `@ExceptionHandler` 方法返回 `Mono<ApiResponse<?>>`
- 保持原有 `ResponseEntity` 或改为直接返回 `ApiResponse`，由框架自动序列化

---

### Requirement: 命名对齐

- Webflux 后端的 `com.example.accounting.document.BillDocument` 迁移到 `com.example.accounting.entity.BillDocument`
- 两个后端的 `BillDocument` 包路径统一为 `entity`
- 其他类名、包名已对齐，无需修改

## MODIFIED Requirements

### Requirement: 现有 Service 接口
所有 Service 类的方法签名从返回 `ApiResponse<T>`（或 `Mono<ApiResponse<T>>`）改为返回裸类型 `T`（或 `Mono<T>`）。

**迁移方式**：
1. 删除 Service 方法中的 `ApiResponse.ok(...)` 和 `ApiResponse.error(...)` 调用
2. 将错误返回改为抛出 `BusinessException`
3. 将成功返回改为直接返回数据对象

## REMOVED Requirements
无功能移除，仅代码结构重构。
