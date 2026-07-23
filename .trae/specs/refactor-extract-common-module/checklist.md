# Checklist

## accounting-common 模块
- [x] `accounting-common/pom.xml` 存在且仅依赖 `lombok`、`spring-data-commons`、`jakarta.validation-api`（provided 作用域）
- [x] 根 `pom.xml` 的 `<modules>` 包含 `accounting-common`
- [x] `accounting-webflux-backend/pom.xml` 包含 `accounting-common` 依赖
- [x] `accounting-webmvc-backend/pom.xml` 包含 `accounting-common` 依赖
- [x] `ApiResponse.java` 存在于 `accounting-common/src/main/java/com/example/accounting/common/`
- [x] `PageResult.java` 存在于 `accounting-common/src/main/java/com/example/accounting/common/`
- [x] `BusinessException.java` 存在于 `accounting-common/src/main/java/com/example/accounting/common/`
- [x] `LoginRequest.java`、`RegisterRequest.java`、`LoginResponse.java`、`BillRequest.java`、`BillQueryRequest.java` 存在于 `accounting-common/src/main/java/com/example/accounting/dto/`
- [x] `User.java`、`Bill.java`、`Category.java`、`BillDocument.java` 存在于 `accounting-common/src/main/java/com/example/accounting/entity/`
- [x] `User`、`Bill`、`Category` 同时保留 R2DBC 的 `@Table`/`@Id` 注解（供 Webflux 使用），MyBatis 不依赖这些注解
- [x] `BillDocument` 统一在 `entity` 包下，不在 `document` 包下

## Webflux 后端 — 删除重复类
- [x] Webflux 后端 `common/ApiResponse.java` 已删除
- [x] Webflux 后端 `common/PageResult.java` 已删除
- [x] Webflux 后端 `common/BusinessException.java` 已删除
- [x] Webflux 后端 `dto/*.java` 已删除
- [x] Webflux 后端 `entity/*.java` 已删除
- [x] Webflux 后端 `document/BillDocument.java` 已删除
- [x] Webflux 后端中所有 `document.BillDocument` 引用已改为 `entity.BillDocument`

## WebMVC 后端 — 删除重复类
- [x] WebMVC 后端 `common/ApiResponse.java` 已删除
- [x] WebMVC 后端 `common/PageResult.java` 已删除
- [x] WebMVC 后端 `common/BusinessException.java` 已删除
- [x] WebMVC 后端 `dto/*.java` 已删除
- [x] WebMVC 后端 `entity/*.java` 已删除

## Service 层去 ApiResponse 化
- [x] Webflux `AuthService.register` 返回 `Mono<String>` 而非 `Mono<ApiResponse<String>>`
- [x] Webflux `AuthService.login` 返回 `Mono<LoginResponse>` 而非 `Mono<ApiResponse<LoginResponse>>`
- [x] Webflux `AuthService.refresh` 返回 `Mono<String>` 而非 `Mono<ApiResponse<String>>`
- [x] Webflux `BillService.createBill` 返回 `Mono<Bill>` 而非 `Mono<ApiResponse<Bill>>`
- [x] Webflux `BillService.updateBill` 返回 `Mono<Bill>` 而非 `Mono<ApiResponse<Bill>>`
- [x] Webflux `BillService.deleteBill` 返回 `Mono<Void>` 而非 `Mono<ApiResponse<Void>>`
- [x] Webflux `BillService.listBills` 返回 `Mono<PageResult<Bill>>` 而非 `Mono<ApiResponse<PageResult<Bill>>>`
- [x] Webflux `CategoryService`、`StatisticsService` 所有方法均已去 `ApiResponse` 化
- [x] WebMVC `AuthService.register` 返回 `String` 而非 `ApiResponse<String>`
- [x] WebMVC `AuthService.login` 返回 `LoginResponse` 而非 `ApiResponse<LoginResponse>`
- [x] WebMVC `AuthService.refresh` 返回 `String` 而非 `ApiResponse<String>`
- [x] WebMVC `BillService.createBill` 返回 `Bill` 而非 `ApiResponse<Bill>`
- [x] WebMVC `BillService.updateBill` 返回 `Bill` 而非 `ApiResponse<Bill>`
- [x] WebMVC `BillService.deleteBill` 返回 `Void` 而非 `ApiResponse<Void>`
- [x] WebMVC `BillService.listBills` 返回 `PageResult<Bill>` 而非 `ApiResponse<PageResult<Bill>>`
- [x] WebMVC `CategoryService`、`StatisticsService` 所有方法均已去 `ApiResponse` 化
- [x] Service 层不再出现 `ApiResponse.ok(...)` 和 `ApiResponse.error(...)` 调用
- [x] 错误场景统一通过 `BusinessException` 抛出（Webflux 用 `Mono.error`，WebMVC 用 `throw`）

## Controller 层适配
- [x] Webflux `AuthController.register` 返回 `authService.register(...).map(ApiResponse::ok)`
- [x] Webflux `AuthController.login` 返回 `authService.login(...).map(ApiResponse::ok)`
- [x] Webflux `AuthController.refresh` 返回 `authService.refresh(...).map(ApiResponse::ok)`
- [x] Webflux 所有 Controller 方法均通过 `.map(ApiResponse::ok)` 包装
- [x] WebMVC `AuthController.register` 直接返回 `authService.register(...)`（裸类型）
- [x] WebMVC `AuthController.login` 直接返回 `authService.login(...)`（裸类型）
- [x] WebMVC `AuthController.refresh` 直接返回 `authService.refresh(...)`（裸类型）
- [x] WebMVC 所有 Controller 方法均直接返回 Service 的裸类型结果

## WebMVC 响应层统一包装
- [x] `ApiResponseAdvice` 类存在且实现 `ResponseBodyAdvice<Object>`
- [x] `ApiResponseAdvice` 对非 `ApiResponse` 类型自动包装为 `ApiResponse.ok(body)`
- [x] `ApiResponseAdvice` 对 `String` 类型正确包装（避免直接返回字符串）
- [x] `ApiResponseAdvice` 对已是 `ApiResponse` 的类型透传，不二次包装

## 全局异常处理器适配
- [x] Webflux `GlobalExceptionHandler` 各方法返回 `Mono<ApiResponse<?>>`
- [x] WebMVC `GlobalExceptionHandler` 各方法返回 `ApiResponse<?>`（不再包裹 `ResponseEntity`）
- [x] WebMVC `GlobalExceptionHandler` 的 `@ExceptionHandler(Exception.class)` 返回 `ApiResponse.error(500, ...)`
- [x] Webflux `GlobalExceptionHandler` 的 `@ExceptionHandler(Exception.class)` 返回 `Mono.just(ApiResponse.error(500, ...))`

## 编译与运行验证
- [x] 根目录执行 `mvn clean compile` 全项目编译通过，无错误
- [x] Webflux 后端服务在 8080 端口正常启动
- [x] WebMVC 后端服务在 8081 端口正常启动
- [x] Webflux `/api/auth/register` 接口返回正确 JSON 格式
- [x] Webflux `/api/auth/login` 接口返回正确 JSON 格式并包含 token
- [x] Webflux `/api/bills` POST 创建账单接口正常工作
- [x] Webflux `/api/bills` GET 查询账单接口正常工作
- [x] Webflux `/api/statistics/monthly` 统计接口正常工作
- [x] WebMVC `/api/auth/register` 接口返回正确 JSON 格式
- [x] WebMVC `/api/auth/login` 接口返回正确 JSON 格式并包含 token
- [x] WebMVC `/api/bills` POST 创建账单接口正常工作
- [x] WebMVC `/api/bills` GET 查询账单接口正常工作
- [x] WebMVC `/api/statistics/monthly` 统计接口正常工作
