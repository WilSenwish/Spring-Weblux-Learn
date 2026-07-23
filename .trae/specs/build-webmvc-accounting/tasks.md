# 双栈记账系统 - The Implementation Plan (Decomposed and Prioritized Task List)

## [x] Task 1: 项目结构重构与重命名
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 将 `accounting-backend` 重命名为 `accounting-webflux-backend`（修改目录名、pom artifactId、父 pom modules）
  - 将 `accounting-frontend` 重命名为 `accounting-webflux-frontend`（修改目录名、pom artifactId）
  - 验证 Webflux 项目代码无需功能修改，仅改名
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `programmatic` TR-1.1: 父 pom modules 包含四个模块
  - `programmatic` TR-1.2: Webflux 后端 artifactId 为 accounting-webflux-backend
  - `programmatic` TR-1.3: 所有 import 路径和包名保持不变（仅目录和 pom 改名）
- **Notes**: 包名 `com.example.accounting` 保持不变，避免大量修改

## [x] Task 2: Web MVC 后端脚手架搭建
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 创建 `accounting-webmvc-backend/pom.xml`，依赖：spring-boot-starter-web、mybatis-spring-boot-starter 3.0.x、pagehelper-spring-boot-starter、mysql-connector-j、spring-boot-starter-data-redis、spring-boot-starter-data-mongodb、spring-boot-starter-security、jjwt 0.12.5、lombok、validation
  - 创建 `application.yml`，端口 8081，数据源、Redis、MongoDB、JWT、mybatis 配置
  - 创建启动类 `AccountingWebmvcApplication`
  - 复用 schema.sql 和 data.sql（放在 resources 下）
  - 包结构：controller / service / mapper / entity / dto / common / config / security
- **Acceptance Criteria Addressed**: AC-2
- **Test Requirements**:
  - `programmatic` TR-2.1: mvn compile 可编译通过
  - `programmatic` TR-2.2: application.yml 端口 8081，mybatis mapper-locations 配置正确
  - `human-judgement` TR-2.3: 分层包结构正确
- **Notes**: MyBatis 使用 3.0.x 适配 Spring Boot 3

## [x] Task 3: Web MVC 通用层与工具类
- **Priority**: high
- **Depends On**: Task 2
- **Description**:
  - 创建 `ApiResponse<T>`（与 Webflux 版完全一致）
  - 创建 `BusinessException`
  - 创建 `GlobalExceptionHandler`（`@RestControllerAdvice`，处理 BusinessException、MethodArgumentNotValidException、Exception）
  - 创建 `PageResult<T>`
  - 创建 `BaseController`（从 SecurityContextHolder 获取当前用户 ID）
  - 创建 CORS 配置（`WebMvcConfigurer` + `addCorsMappings`）
  - 创建请求日志拦截器（`HandlerInterceptor` + `WebMvcConfigurer` 注册）
- **Acceptance Criteria Addressed**: AC-2
- **Test Requirements**:
  - `programmatic` TR-3.1: ApiResponse 字段与 Webflux 版完全一致
  - `programmatic` TR-3.2: 全局异常处理器正确返回 ApiResponse
  - `human-judgement` TR-3.3: 代码风格与 Webflux 版一致
- **Notes**: 确保前端无需修改即可对接

## [x] Task 4: Web MVC 用户认证模块
- **Priority**: high
- **Depends On**: Task 3
- **Description**:
  - 创建实体类 `User`
  - 创建 MyBatis `UserMapper` 接口 + `UserMapper.xml`
  - 创建 `RegisterRequest`、`LoginRequest`、`LoginResponse` DTO
  - 创建 `JwtUtil` 工具类（jjwt 0.12.5，阻塞式）
  - 创建 `JwtAuthenticationFilter`（`OncePerRequestFilter`）
  - 创建 `UserDetailsServiceImpl`（实现 `UserDetailsService`）
  - 创建 `SecurityConfig`（`@EnableWebSecurity` + `SecurityFilterChain`，STATELESS）
  - 创建 `AuthService`：注册、登录、刷新
  - 创建 `AuthController`：`/api/auth/register`、`/api/auth/login`、`/api/auth/refresh`
- **Acceptance Criteria Addressed**: AC-3
- **Test Requirements**:
  - `programmatic` TR-4.1: 注册接口与 Webflux 版返回一致
  - `programmatic` TR-4.2: 登录接口返回 JWT Token
  - `programmatic` TR-4.3: 未认证请求返回 401 JSON
- **Notes**: 接口路径、字段与 Webflux 版完全一致

## [x] Task 5: Web MVC 分类与记账模块
- **Priority**: high
- **Depends On**: Task 4
- **Description**:
  - 创建实体类 `Category`、`Bill`
  - 创建 MongoDB 文档类 `BillDocument`（与 Bill 字段一致 + `@Id` 字符串）
  - 创建 `CategoryMapper` 接口 + XML（增删改查、用户+预设查询）
  - 创建 `BillMapper` 接口 + XML（增删改查、动态条件查询）
  - 创建 `BillDocumentRepository`（MongoRepository，用于 MongoDB 操作）
  - 创建 `CategoryService` + `CategoryController`（`/api/categories`）
  - 创建 `BillService` + `BillController`（`/api/bills`），分页用 PageHelper
  - **关键**：BillService 中新增/修改/删除时同步操作 MongoDB（保存原始记账记录明细）
- **Acceptance Criteria Addressed**: AC-4, AC-5
- **Test Requirements**:
  - `programmatic` TR-5.1: 分类 CRUD 功能完整
  - `programmatic` TR-5.2: 记账 CRUD 功能完整，分页正确
  - `programmatic` TR-5.3: 新增/修改/删除账单时 MongoDB 同步更新
  - `programmatic` TR-5.4: 动态条件查询正确
- **Notes**: 预设分类不可删除

## [x] Task 6: Web MVC 统计分析模块
- **Priority**: high
- **Depends On**: Task 5
- **Description**:
  - 创建 `TimePeriodStat`、`CategoryStat`、`StatisticsResponse` DTO
  - 创建 `StatisticsMapper` 接口 + XML（周/月/年/分类统计 SQL）
  - 创建 `StatisticsService`：周统计、月统计、年统计、分类统计，缺失日期补零
  - 使用 `RedisTemplate` 缓存热点统计数据，TTL 5 分钟
  - 创建 `StatisticsController`（`/api/statistics/weekly`、`/monthly`、`/yearly`、`/category`）
- **Acceptance Criteria Addressed**: AC-6
- **Test Requirements**:
  - `programmatic` TR-6.1: 四种统计接口返回结构与 Webflux 版一致
  - `programmatic` TR-6.2: Redis 缓存生效
  - `programmatic` TR-6.3: 缺失日期/月份补零
- **Notes**: 统计 SQL 写在 MyBatis XML 中

## [x] Task 7: Webflux 后端补充 MongoDB 原始明细
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 在 Webflux 后端创建 `BillDocument`（MongoDB 响应式文档）
  - 创建 `ReactiveBillDocumentRepository`（ReactiveMongoRepository）
  - 修改 `BillService`，在新增/修改/删除账单时同步写入 MongoDB（响应式）
  - 确保与 Web MVC 版的 MongoDB 文档结构一致
- **Acceptance Criteria Addressed**: AC-5
- **Test Requirements**:
  - `programmatic` TR-7.1: Webflux 版账单操作同步写入 MongoDB
  - `programmatic` TR-7.2: 文档结构与 Web MVC 版一致
- **Notes**: 使用 ReactiveMongoTemplate 或 ReactiveMongoRepository

## [x] Task 8: Web MVC 前端创建
- **Priority**: high
- **Depends On**: Task 6
- **Description**:
  - 复制 `accounting-webflux-frontend` 为 `accounting-webmvc-frontend`
  - 修改 package.json 的 name
  - 修改 vite.config.js 的代理 target 为 `http://localhost:8081`
  - 修改 pom.xml artifactId
  - 确保所有功能正常
- **Acceptance Criteria Addressed**: AC-7
- **Test Requirements**:
  - `programmatic` TR-8.1: 前端可独立启动，代理到 8081
  - `programmatic` TR-8.2: 所有页面功能正常
- **Notes**: 业务代码与 webflux 版完全一致，仅代理和包名不同

## [x] Task 9: 架构对比文档
- **Priority**: medium
- **Depends On**: Task 8
- **Description**:
  - 创建 `docs/architecture-comparison.md`
  - 内容：技术栈对比表、架构模式差异（响应式 vs MVC）、线程模型对比、数据库访问层对比（R2DBC vs MyBatis）、Redis/MongoDB 响应式 vs 阻塞式对比、适用场景建议、代码组织差异、启动端口与模块说明
  - 使用 Mermaid 绘制简化架构图
- **Acceptance Criteria Addressed**: AC-8
- **Test Requirements**:
  - `human-judgement` TR-9.1: 对比维度完整，描述准确
  - `human-judgement` TR-9.2: 架构图清晰易懂
- **Notes**: 中文文档

## [x] Task 10: 使用文档
- **Priority**: medium
- **Depends On**: Task 9
- **Description**:
  - 创建 `docs/README.md`（项目总览、模块说明、快速开始）
  - 创建 `docs/usage-guide.md`（环境准备：MySQL 8.4.6 / MongoDB 7.0.24 / Redis Stack 7.2.0-v10、启动步骤、如何切换前后端）
  - 创建 `docs/api-reference.md`（所有接口详细说明：路径、方法、请求、响应、示例）
  - 创建 `docs/mongodb-design.md`（MongoDB 存储设计、原始明细说明）
- **Acceptance Criteria Addressed**: AC-9
- **Test Requirements**:
  - `human-judgement` TR-10.1: 文档完整清晰，新用户可快速上手
  - `human-judgement` TR-10.2: 接口文档准确
- **Notes**: 中文文档

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 3
- Task 5 depends on Task 4
- Task 6 depends on Task 5
- Task 7 depends on Task 1
- Task 8 depends on Task 6, Task 7
- Task 9 depends on Task 8
- Task 10 depends on Task 9
