# 双栈记账系统 - 使用文档

## 一、项目简介

本项目是一个基于 Spring Boot 3 的记账系统，提供了 **Webflux（响应式）** 和 **Web MVC（传统阻塞式）** 两套技术栈的后端实现，以及对应的 Vue3 前端。两套后端共享同一套数据库，对外提供完全一致的 API 接口。

### 功能特性

- 用户注册 / 登录（JWT 认证）
- 记账分类管理（收入 / 支出，支持预设和自定义分类）
- 账单记录（增删改查、分页、多条件筛选）
- 统计分析（按周 / 月 / 年 / 分类统计，支持图表展示）
- Redis 缓存热点统计数据
- MongoDB 存储原始记账记录明细
- **账本管理**：个人账本（type=1）/ 共享账本（type=2），多用户共用账本，一个用户可加入多个账本
- **分类与账本绑定**：自定义分类绑定账本 ID，仅在账本内可见；预设分类全局共享
- **账本成员权限**：账本所有者/管理员可配置 `allowMemberEdit`，控制普通成员是否能编辑彼此的账单
- **JWT 工具公共化**：核心逻辑下沉至 `accounting-common` 模块，Webflux 适配 Mono 包装，WebMVC 直接复用

---

## 学习路径

本项目最大的特色是"**双栈同业务**"实现，是学习 Spring Webflux 异步非阻塞编程的优秀案例。推荐阅读顺序：

1. [项目使用文档](README.md) — 了解功能与部署
2. [架构对比](architecture-comparison.md) — 理解双栈技术选型
3. [Webflux 对照学习指南](webflux-learning-guide.md) — 通过 WebMvc vs Webflux 同一业务代码对比学习
4. [数据库简表](db-schema.md) — 了解数据模型
5. [MySQL 表设计详解](mysql-design.md) — 字段定义、索引、建表 SQL 完整版
6. [API 参考](api-reference.md) — 了解接口规范
7. [MongoDB 设计](mongodb-design.md) — 了解双数据源设计

---

## 二、核心业务说明

### 2.1 账本功能

- **个人账本（type=1）**：用户创建后默认归属本人，账本内账单与分类对所有者完全私有。
- **共享账本（type=2）**：所有者可邀请其他用户加入，账本内的账单由多用户共同维护。
- 一个用户可同时拥有多个账本，并在不同账本间自由切换。
- 账本包含基础属性：账本名称、类型、所有者、创建时间、成员列表等。

### 2.2 分类与账本绑定

- **预设分类**：`is_preset=1`，由系统预置，全局共享，所有账本可见（如工资、餐饮、交通等）。
- **自定义分类**：`is_preset=0`，由用户在某个账本下创建，绑定 `ledger_id`，仅在该账本内可见。
- 同一分类名称可在不同账本下独立创建（通过 `(ledger_id, name)` 唯一约束区分）。

### 2.3 账本成员权限控制（allowMemberEdit）

账本 `ledger` 表中 `allow_member_edit` 字段用于控制成员间的编辑权限：

| 取值 | 含义 |
|------|------|
| `1` | 成员可互相编辑账单（协作型共享账本） |
| `0` | 成员仅能编辑自己创建的账单（家庭/私密共享账本） |

> **注意**：账本的所有者（owner）和管理员（admin）不受该字段限制，始终拥有完整编辑权限。

### 2.4 JWT 工具类公共化

为避免两个后端重复实现 JWT 解析/签发逻辑，将核心工具下沉到 `accounting-common` 模块：

- **WebMVC**：`JwtUtil` 继承自 `accounting-common.JwtUtil`，直接复用同步方法（`generateToken`、`extractUsername`、`validateToken`）。
- **Webflux**：`JwtUtil` 继承自 `accounting-common.JwtUtil`，新增带 `Reactive` 后缀的响应式方法（`generateTokenReactive`、`extractUsernameReactive`、`validateTokenReactive`、`isTokenExpiredReactive`），通过 `Mono.fromCallable` 包装同步实现，避免阻塞 Reactor 线程。

### 2.5 Webflux 响应层重构

Webflux 中不存在 `ResponseBodyAdvice` 抽象，因此全局响应封装采用 WebFilter 方案：

- **Controller 层**：直接返回 `Mono<T>`（不再返回 `Mono<ApiResponse<T>>`）。
- **ApiResponseWebFilter**：通过 `WebFilter` + `ServerHttpResponseDecorator` 拦截响应，将业务返回值统一包装为 `ApiResponse<T>`。
- **优点**：Controller 聚焦业务返回，公共包装逻辑集中在 Filter 层，与 WebMVC 的 `ApiResponseAdvice` 职责对齐。

### 2.6 跨平台 Docker 部署

为方便开发者在不同操作系统上启动数据库服务，项目在 `docs/docker-compose/` 目录下提供三套 Docker Compose 配置：

| 平台 | 路径 |
|------|------|
| macOS | `docs/docker-compose/macos/docker-compose.yml` |
| Windows | `docs/docker-compose/windows/docker-compose.yml` |
| Linux | `docs/docker-compose/linux/docker-compose.yml` |

启动方式（以 macOS 为例）：

```bash
cd docs/docker-compose/macos
docker compose up -d
```

---

## 三、环境准备

### 3.1 数据库版本

| 服务 | 版本 | 端口 | 用途 |
|------|------|------|------|
| MySQL | 8.4.6 | 3306 | 结构化数据存储（用户、分类、账单） |
| MongoDB | 7.0.24 | 27017 | 原始记账记录明细存储 |
| Redis Stack | 7.2.0-v10 | 6379 | 会话缓存、统计数据缓存 |

### 3.2 开发环境

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | Spring Boot 3 要求 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+ | 前端构建 |
| npm / pnpm | - | 包管理 |

> 项目使用 Mise 管理 SDK 版本，可通过 `mise install` 自动安装。

---

## 四、快速开始

### 4.1 初始化数据库

#### MySQL 初始化

> 本项目已包含完整的建表 SQL 与字段说明（涵盖 `ledger`、`ledger_member` 表，以及 `ledger_id`、`allow_member_edit`、`salt` 等字段），为避免文档冗余，此处不再列出。请前往以下文档查阅：
>
> - [数据库简表（db-schema.md）](db-schema.md)：表结构概览与表间关系
> - [MySQL 表设计详解（mysql-design.md）](mysql-design.md)：字段定义、索引、约束、建表 SQL 完整版

也可直接使用各后端模块自带的 `src/main/resources/schema.sql` 启动时自动建表（推荐）。

#### MongoDB 说明

MongoDB 无需手动创建集合，程序首次写入时会自动创建 `bill_records` 集合。

### 4.2 启动 Webflux 版本

#### 启动后端

```bash
cd accounting-webflux-backend
mvn spring-boot:run
```

后端服务启动在 **http://localhost:8080**

#### 启动前端

```bash
cd accounting-webflux-frontend
npm install
npm run dev
```

前端服务启动在 **http://localhost:3000**，默认代理到 8080 端口。

### 4.3 启动 Web MVC 版本

#### 启动后端

```bash
cd accounting-webmvc-backend
mvn spring-boot:run
```

后端服务启动在 **http://localhost:8081**

#### 启动前端

```bash
cd accounting-webmvc-frontend
npm install
npm run dev
```

前端服务启动在 **http://localhost:3000**，默认代理到 8081 端口。

---

## 五、如何切换前后端

### 方案一：使用对应版本的前端

- `accounting-webflux-frontend` → 对接 Webflux 后端（8080）
- `accounting-webmvc-frontend` → 对接 Web MVC 后端（8081）

### 方案二：修改前端代理配置

编辑对应前端项目的 `vite.config.js`：

```js
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8081', // 修改为要对接的后端端口
      changeOrigin: true
    }
  }
}
```

修改后重启前端开发服务器即可。

---

## 六、项目结构说明

```
Spring-Weblux-Learn/
├── accounting-common/                # 公共模块（ApiResponse/Entity/DTO/JwtUtil 核心等）
│   └── src/main/java/com/example/accounting/
│       ├── common/                   # ApiResponse、BusinessException、PageResult
│       ├── security/                 # JwtUtil（核心逻辑）
│       ├── dto/                      # 请求/响应 DTO
│       └── entity/                   # 实体类（User/Bill/Category/Ledger/LedgerMember）
│
├── accounting-webflux-backend/        # Webflux 响应式后端（端口 8080）
│   ├── src/main/java/.../accounting/
│   │   ├── common/                   # GlobalExceptionHandler
│   │   ├── config/                   # 配置（CORS、ApiResponseWebFilter、RequestLogFilter）
│   │   ├── controller/               # 控制器层（返回 Mono<T>）
│   │   ├── repository/               # R2DBC + ReactiveMongo Repository
│   │   ├── security/                 # JwtUtil（继承公共类，扩展 Reactive 方法）
│   │   └── service/                  # 业务逻辑层（全 Mono/Flux）
│   └── src/main/resources/
│       ├── application.yml
│       └── schema.sql / data.sql
│
├── accounting-webmvc-backend/         # Web MVC 传统后端（端口 8081）
│   ├── src/main/java/.../accounting/
│   │   ├── common/                   # GlobalExceptionHandler
│   │   ├── config/                   # 配置（ApiResponseAdvice、CORS、拦截器）
│   │   ├── controller/               # 控制器层（直接返回 ApiResponse<T>）
│   │   ├── mapper/                   # MyBatis Mapper 接口
│   │   ├── repository/               # MongoDB Repository
│   │   ├── security/                 # JwtUtil（继承公共类）
│   │   └── service/                  # 业务逻辑层（同步方法）
│   └── src/main/resources/
│       ├── mapper/                   # MyBatis XML 映射
│       ├── application.yml
│       └── schema.sql / data.sql
│
├── accounting-webflux-frontend/       # Webflux 对应前端
├── accounting-webmvc-frontend/        # Web MVC 对应前端
│
└── docs/                              # 项目文档
    ├── README.md                      # 本文档
    ├── architecture-comparison.md     # 架构对比文档
    ├── webflux-learning-guide.md      # Webflux 对照学习指南
    ├── db-schema.md                   # 数据库简表
    ├── api-reference.md               # API 参考文档
    ├── mongodb-design.md              # MongoDB 设计文档
    └── docker-compose/                # 跨平台 Docker Compose 配置
        ├── macos/docker-compose.yml
        ├── windows/docker-compose.yml
        └── linux/docker-compose.yml
```

---

## 七、配置说明

### 后端核心配置（application.yml）

```yaml
server:
  port: 8080    # Webflux 版本；Web MVC 版本为 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/accounting?...
    username: root
    password: 123456
  data:
    mongodb:
      uri: mongodb://localhost:27017/accounting
    redis:
      host: localhost
      port: 6379
      database: 0

jwt:
  secret: accounting-jwt-secret-key-2026-change-in-production
  expiration: 86400000   # 24小时
```

> **注意**：生产环境请务必修改 JWT 密钥和数据库密码！

---

## 八、常见问题

### Q1: 启动后端时报数据库连接错误？
A: 请确认 MySQL、MongoDB、Redis 服务已启动，且配置文件中的连接信息正确。

### Q2: Webflux 后端编译失败？
A: 请确认使用 JDK 17 及以上版本，Mise 用户可执行 `mise use java@zulu-17`。

### Q3: 两套后端可以同时运行吗？
A: 可以。它们使用不同的端口（8080 / 8081），但需要注意两套后端是**相互独立**的系统，会使用不同的 MySQL database（`webflux_db` / `webmvc_db`）和不同的 MongoDB database（`webflux_mongo` / `webmvc_mongo`），即同一物理数据库实例下的不同 schema。如需修改请调整各后端 `application.yml` 中的 `spring.datasource.url` 与 `spring.data.mongodb.uri`。

### Q4: 如何验证 MongoDB 同步是否正常？
A: 使用 MongoDB Shell 或 Compass 连接数据库，查看 `accounting.bill_records` 集合，新增/修改/删除账单后集合内容应同步变化。

---

## 九、项目目录结构

```
Spring-Weblux-Learn/
├── pom.xml                             # 父 POM（统一管理 5 个子模块）
│
├── accounting-common/                  # ① 公共模块（被两套后端依赖）
│   ├── pom.xml
│   └── src/main/java/com/example/accounting/
│       ├── common/                     # ApiResponse、BusinessException、PageResult
│       ├── security/                   # JwtUtil（核心实现）
│       ├── dto/                        # BillRequest、CategoryRequest、LoginRequest、...
│       └── entity/                     # User、Bill、Category、Ledger、LedgerMember、BillDocument
│
├── accounting-webflux-backend/         # ② Webflux 响应式后端（端口 8080）
│   ├── pom.xml
│   └── src/main/java/com/example/accounting/
│       ├── AccountingWebfluxApplication.java
│       ├── common/                     # GlobalExceptionHandler
│       ├── config/                     # ApiResponseWebFilter、CorsConfig、R2dbcConfig、RedisConfig
│       ├── controller/                 # AuthController、BillController、CategoryController、LedgerController、StatisticsController
│       ├── repository/                 # BillRepository、LedgerRepository、UserRepository、ReactiveBillDocumentRepository
│       ├── security/                   # JwtUtil（继承 common，扩展 Reactive 方法）、JwtAuthenticationFilter、SecurityConfig
│       └── service/                    # AuthService、BillService、CategoryService、LedgerService、StatisticsService
│
├── accounting-webmvc-backend/          # ③ Web MVC 传统后端（端口 8081）
│   ├── pom.xml
│   └── src/main/java/com/example/accounting/
│       ├── AccountingWebmvcApplication.java
│       ├── common/                     # GlobalExceptionHandler
│       ├── config/                     # ApiResponseAdvice、CorsConfig、WebMvcConfig、RequestLogInterceptor
│       ├── controller/                 # AuthController、BillController、CategoryController、LedgerController、StatisticsController
│       ├── mapper/                     # UserMapper、BillMapper、CategoryMapper、LedgerMapper、LedgerMemberMapper、StatisticsMapper
│       ├── repository/                 # BillDocumentRepository（MongoDB）
│       ├── security/                   # JwtUtil（继承 common）、JwtAuthenticationFilter、SecurityConfig
│       └── service/                    # AuthService、BillService、CategoryService、LedgerService、StatisticsService
│
├── accounting-webflux-frontend/        # ④ Webflux 对应前端（Vue3 + Vite，代理 8080）
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── views/                      # LoginView、RegisterView、BillListView、CategoryView、StatisticsView
│       ├── components/                 # BillFormDialog、CategoryStatChart、PeriodStatChart
│       ├── layout/                     # Layout.vue
│       ├── router/                     # Vue Router
│       ├── stores/                     # Pinia
│       └── utils/                      # request.js（Axios 封装）
│
├── accounting-webmvc-frontend/         # ⑤ Web MVC 对应前端（Vue3 + Vite，代理 8081）
│   ├── package.json
│   ├── vite.config.js
│   └── src/                            # 同 webflux 前端，业务代码一致，仅代理端口不同
│
├── docs/                               # 项目文档
│   ├── README.md                       # 本文档
│   ├── architecture-comparison.md      # 双栈架构对比
│   ├── webflux-learning-guide.md       # Webflux 对照学习指南
│   ├── db-schema.md                    # 数据库表结构
│   ├── api-reference.md                # API 接口参考
│   ├── mongodb-design.md               # MongoDB 存储设计
│   └── docker-compose/                 # 跨平台 Docker 编排
│       ├── macos/docker-compose.yml
│       ├── windows/docker-compose.yml
│       └── linux/docker-compose.yml
│
└── mise.toml                           # SDK 版本管理（Java/Node/Maven）
```

> **模块职责一句话总结**
> - `accounting-common`：放 DTO、Entity、ApiResponse、JwtUtil 核心逻辑，两套后端共享。
> - `accounting-webflux-backend`：R2DBC + ReactiveRedis + ReactiveMongo，全链路 Mono/Flux。
> - `accounting-webmvc-backend`：MyBatis + RedisTemplate + MongoRepository，同步阻塞风格。
> - `accounting-webflux-frontend` / `accounting-webmvc-frontend`：UI 几乎完全一致，仅 Vite 代理端口不同。

---

## 十、项目总结与学习路径

本项目的最大特色是：**双栈同业务、纯对照学习 Webflux 的最佳实践**。同一套记账业务分别用 WebMVC（同步阻塞）与 Webflux（响应式）实现，读者可逐文件对照 `BillService.listBills` 等业务方法，以"同步写法为锚"快速理解 Mono/Flux、响应式 Repository、Reactive JwtUtil 等概念的差异。

### 推荐学习路径

1. [docs/README.md](README.md) — 业务总览：账本/分类/账单/权限等核心模型
2. [docs/architecture-comparison.md](architecture-comparison.md) — 架构对比：双栈技术选型与模块划分
3. [docs/webflux-learning-guide.md](webflux-learning-guide.md) — 对照学习：WebMVC vs Webflux 同一业务代码的逐行对照
4. [docs/db-schema.md](db-schema.md) — 数据模型：表结构与表间关系
5. [docs/mysql-design.md](mysql-design.md) — 表设计：字段定义、索引、建表 SQL 完整版
6. [docs/api-reference.md](api-reference.md) — 接口规范：两套后端一致的 REST API
7. [docs/mongodb-design.md](mongodb-design.md) — 双数据源：MongoDB 存储与同步策略

### 学习建议

- **以同步写法为锚**：先读 WebMVC 版 `BillService.listBills` 等业务方法建立认知，再对照看 Webflux 版的 `Mono`/`Flux` 写法，理解 `flatMap`、`zipWith`、`switchIfEmpty` 等响应式操作符的等价语义。
- **重点关注差异点**：Repository 层（R2DBC vs MyBatis）、过滤器层（WebFilter vs HandlerInterceptor）、异常处理（`onErrorResume` vs `try/catch`）、JwtUtil（`Mono.fromCallable` 包装同步逻辑）等。
- **结合业务理解设计**：账本/分类/成员权限的业务复杂度足以体现响应式编程的收益，也能避免陷入"Hello World 级别的响应式玩具"陷阱。
