# Tasks

- [x] Task 1: 创建 `accounting-common` Maven 模块并配置 pom.xml
  - [x] SubTask 1.1: 在根目录下创建 `accounting-common/` 目录结构（`src/main/java/com/example/accounting/` 下的 `common`、`dto`、`entity` 包）
  - [x] SubTask 1.2: 编写 `accounting-common/pom.xml`，仅依赖 `lombok`、`spring-data-commons`、`jakarta.validation-api`（均 provided）
  - [x] SubTask 1.3: 修改根 `pom.xml`，在 `<modules>` 中增加 `accounting-common`

- [x] Task 2: 迁移公共类到 `accounting-common` 模块
  - [x] SubTask 2.1: 迁移 `common` 包下 `ApiResponse.java`、`PageResult.java`、`BusinessException.java`
  - [x] SubTask 2.2: 迁移 `dto` 包下 `LoginRequest.java`、`RegisterRequest.java`、`LoginResponse.java`、`BillRequest.java`、`BillQueryRequest.java`
  - [x] SubTask 2.3: 迁移 `entity` 包下 `User.java`、`Bill.java`、`Category.java`、`BillDocument.java`（合并双栈差异：保留 R2DBC `@Table`/`@Id` 和 MongoDB `@Document`/`@Id` 注解，MyBatis 不依赖这些注解）

- [x] Task 3: 修改 Webflux 后端 — 删除重复类并添加模块依赖
  - [x] SubTask 3.1: 修改 `accounting-webflux-backend/pom.xml`，添加 `accounting-common` 依赖
  - [x] SubTask 3.2: 删除 Webflux 后端中已被抽取的 `common/*`、`dto/*`、`entity/*`、`document/BillDocument.java` 文件
  - [x] SubTask 3.3: 修改 Webflux 后端中引用 `document.BillDocument` 的代码，改为 `entity.BillDocument`

- [x] Task 4: 修改 WebMVC 后端 — 删除重复类并添加模块依赖
  - [x] SubTask 4.1: 修改 `accounting-webmvc-backend/pom.xml`，添加 `accounting-common` 依赖
  - [x] SubTask 4.2: 删除 WebMVC 后端中已被抽取的 `common/*`、`dto/*`、`entity/*` 文件

- [x] Task 5: Webflux Service 层去 ApiResponse 化
  - [x] SubTask 5.1: 改造 `AuthService`：方法返回 `Mono<T>`，错误场景抛 `Mono.error(new BusinessException(...))`
  - [x] SubTask 5.2: 改造 `BillService`：方法返回 `Mono<T>`，错误场景抛 `Mono.error(new BusinessException(...))`
  - [x] SubTask 5.3: 改造 `CategoryService`：方法返回 `Mono<T>`
  - [x] SubTask 5.4: 改造 `StatisticsService`：方法返回 `Mono<T>`

- [x] Task 6: WebMVC Service 层去 ApiResponse 化
  - [x] SubTask 6.1: 改造 `AuthService`：方法返回 `T`，错误场景 `throw new BusinessException(...)`
  - [x] SubTask 6.2: 改造 `BillService`：方法返回 `T`，错误场景 `throw new BusinessException(...)`
  - [x] SubTask 6.3: 改造 `CategoryService`：方法返回 `T`
  - [x] SubTask 6.4: 改造 `StatisticsService`：方法返回 `T`

- [x] Task 7: Webflux Controller 层适配新返回类型
  - [x] SubTask 7.1: 改造 `AuthController`：Service 调用后追加 `.map(ApiResponse::ok)`
  - [x] SubTask 7.2: 改造 `BillController`：Service 调用后追加 `.map(ApiResponse::ok)`
  - [x] SubTask 7.3: 改造 `CategoryController`：Service 调用后追加 `.map(ApiResponse::ok)`
  - [x] SubTask 7.4: 改造 `StatisticsController`：Service 调用后追加 `.map(ApiResponse::ok)`

- [x] Task 8: WebMVC Controller 层适配新返回类型
  - [x] SubTask 8.1: 改造 `AuthController`：直接返回 Service 调用结果（由 ResponseBodyAdvice 自动包装）
  - [x] SubTask 8.2: 改造 `BillController`：直接返回 Service 调用结果
  - [x] SubTask 8.3: 改造 `CategoryController`：直接返回 Service 调用结果
  - [x] SubTask 8.4: 改造 `StatisticsController`：直接返回 Service 调用结果

- [x] Task 9: WebMVC 响应层统一包装 — 创建 `ApiResponseAdvice`
  - [x] SubTask 9.1: 创建 `config/ApiResponseAdvice.java` 实现 `ResponseBodyAdvice<Object>`
  - [x] SubTask 9.2: 处理 `String` 返回类型特殊场景（避免与 StringHttpMessageConverter 冲突）
  - [x] SubTask 9.3: 确保已返回 `ApiResponse` 的类型不再二次包装

- [x] Task 10: 全局异常处理器适配
  - [x] SubTask 10.1: 改造 Webflux `GlobalExceptionHandler`：返回 `Mono<ApiResponse<?>>`
  - [x] SubTask 10.2: 改造 WebMVC `GlobalExceptionHandler`：返回 `ApiResponse<?>`（不再包 ResponseEntity）

- [x] Task 11: 编译验证
  - [x] SubTask 11.1: 执行根目录 `mvn clean compile` 确保全项目编译通过
  - [x] SubTask 11.2: 分别启动 Webflux（8080）和 WebMVC（8081）后端服务
  - [x] SubTask 11.3: 执行接口测试（注册、登录、创建账单、查询账单、统计接口）验证功能正常

# Task Dependencies
- Task 3 depends on Task 1, Task 2
- Task 4 depends on Task 1, Task 2
- Task 5, Task 6 can run in parallel after Task 3, Task 4
- Task 7 depends on Task 5
- Task 8 depends on Task 6
- Task 9 depends on Task 8
- Task 10 depends on Task 7, Task 9
- Task 11 depends on all previous tasks
