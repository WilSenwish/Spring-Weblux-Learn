# 双栈记账系统 - Product Requirement Document

## Overview
- **Summary**: 在现有项目基础上，重构并扩展为 Webflux 与 Web MVC 双技术栈的记账系统：原有项目重命名为 `accounting-webflux-backend` / `accounting-webflux-frontend`，新增 `accounting-webmvc-backend` / `accounting-webmvc-frontend`，两套系统业务功能完全一致，均使用 MongoDB 存储原始记账记录明细。最终输出双栈架构对比文档和使用文档。
- **Purpose**: 提供同一业务的两种完整技术栈实现（响应式 vs 传统 MVC），用于对比学习、选型参考和团队培训；通过完整文档降低上手成本。
- **Target Users**: Java 后端开发者、全栈开发者、架构学习者、技术选型决策者

## Goals
- 原有 `accounting-backend` 重命名为 `accounting-webflux-backend`，`accounting-frontend` 重命名为 `accounting-webflux-frontend`
- 新增 `accounting-webmvc-backend`：Spring Boot 3.5.16 + Spring Web MVC + MyBatis + PageHelper + Redis + MySQL + MongoDB
- 新增 `accounting-webmvc-frontend`：Vue3 前端（与 Webflux 版功能一致，独立模块便于对比）
- 两套后端均使用 MongoDB 存储原始记账记录明细（MySQL 存结构化数据，MongoDB 存原始明细快照）
- 输出 Webflux vs Web MVC 架构对比文档
- 输出完整使用文档（环境准备、启动步骤、接口清单、常见问题）

## Non-Goals (Out of Scope)
- 不新增业务功能，严格保持两套系统功能一致
- 不进行性能基准测试对比（架构文档仅说明理论差异）
- 不改变前端技术栈（Vue3 + Vite + Element Plus + ECharts）
- 不引入新的第三方依赖

## Background & Context
- 现有项目 `accounting-backend` 使用 Spring Boot 3.5.16 + Webflux 全响应式栈
- 现有前端 `accounting-frontend` 使用 Vue3 + Element Plus + ECharts
- 用户规则：Controller → Service → Mapper 分层，Lombok，统一 ApiResponse，异常处理模式
- 两套后端接口路径、请求/响应格式完全一致，前端可无缝切换

## Functional Requirements
- **FR-1**: 用户注册与登录，JWT Token 认证、Token 刷新（两套后端接口完全一致）
- **FR-2**: 分类 CRUD，支持系统预设分类与用户自定义分类
- **FR-3**: 记账 CRUD，支持分页、按类型/分类/日期范围筛选
- **FR-4**: 按周/月/年/分类的统计分析，Redis 缓存热点数据
- **FR-5**: MongoDB 存储原始记账记录明细（新增/修改/删除账单时同步写入 MongoDB，可用于审计或数据追溯）
- **FR-6**: 全局异常处理、统一 ApiResponse 响应封装、请求日志、CORS 跨域
- **FR-7**: Webflux 与 Web MVC 架构对比文档
- **FR-8**: 项目使用文档（环境准备、启动步骤、接口清单、常见问题）

## Non-Functional Requirements
- **NFR-1**: Web MVC 后端分层遵循 Controller → Service → Mapper，禁止 Controller 写业务
- **NFR-2**: 必须使用 MyBatis + PageHelper 实现数据访问与分页
- **NFR-3**: 必须使用 Lombok，禁止手写 get/set
- **NFR-4**: 两套后端接口路径、请求/响应格式完全一致
- **NFR-5**: MongoDB 存储原始记账记录明细，字段与 MySQL bill 表一致，另存 MongoDB 文档 ID
- **NFR-6**: 端口可配置，默认 Webflux 后端 8080，Web MVC 后端 8081

## Constraints
- **Technical**:
  - Webflux 栈：Spring Boot 3.5.16 + Webflux + R2DBC + Reactive Redis + Reactive MongoDB + Security Reactive
  - Web MVC 栈：Spring Boot 3.5.16 + Web MVC + MyBatis + PageHelper + MySQL JDBC + RedisTemplate + MongoTemplate + Spring Security
  - Java 17+
  - 前端：Vue3 + Vite + Element Plus + ECharts
- **Business**:
  - 业务功能 100% 对齐
  - 接口路径、请求/响应字段完全一致
- **Dependencies**:
  - MySQL 8.4.6（存用户、分类、账单结构化数据）
  - MongoDB 7.0.24（存原始记账记录明细）
  - Redis Stack 7.2.0-v10（存会话、缓存统计数据）

## Assumptions
- 用户已安装 JDK 17+、Maven 3.6+、Node.js 16+、MySQL 8.4.6、MongoDB 7.0.24、Redis Stack 7.2.0-v10
- 两套后端使用同一个 MySQL 数据库、同一个 Redis、同一个 MongoDB（可通过配置区分）
- 前端代理 target 可配置，切换到不同后端

## Acceptance Criteria

### AC-1: 项目重命名完成，结构清晰
- **Given**: 原有项目存在
- **When**: 完成重命名和新模块创建
- **Then**: 目录结构为 `accounting-webflux-backend` / `accounting-webflux-frontend` / `accounting-webmvc-backend` / `accounting-webmvc-frontend`，父 pom 聚合四个模块
- **Verification**: `programmatic`

### AC-2: Web MVC 后端可正常启动
- **Given**: 数据库环境就绪
- **When**: 启动 `accounting-webmvc-backend`
- **Then**: 服务在 8081 端口正常启动，无报错
- **Verification**: `programmatic`

### AC-3: 用户认证功能完整（双栈一致）
- **Given**: 服务运行
- **When**: 调用注册/登录/刷新接口
- **Then**: 两套后端返回格式完全一致，均返回 JWT Token
- **Verification**: `programmatic`

### AC-4: 分类与记账 CRUD 完整（双栈一致）
- **Given**: 用户已登录
- **When**: 调用分类和记账接口
- **Then**: 数据正确读写，分页正确，两套后端返回格式一致
- **Verification**: `programmatic`

### AC-5: MongoDB 存储原始记账明细
- **Given**: 新增/修改/删除账单
- **When**: 操作完成后
- **Then**: MongoDB 中同步写入/更新/删除对应原始记录明细文档
- **Verification**: `programmatic`

### AC-6: 统计分析完整（双栈一致）
- **Given**: 有记账数据
- **When**: 调用统计接口
- **Then**: 返回数据结构一致，Redis 缓存生效
- **Verification**: `programmatic`

### AC-7: 双前端可独立运行
- **Given**: 两套后端分别运行
- **When**: 启动对应前端
- **Then**: `accounting-webflux-frontend` 对接 8080，`accounting-webmvc-frontend` 对接 8081，功能均正常
- **Verification**: `programmatic`

### AC-8: 架构对比文档完整
- **Given**: 两个后端项目均已实现
- **When**: 查看架构文档
- **Then**: 包含技术栈对比表、架构图、线程模型差异、数据库访问层对比、适用场景建议
- **Verification**: `human-judgment`

### AC-9: 使用文档完整
- **Given**: 文档已生成
- **When**: 新用户按文档操作
- **Then**: 30 分钟内完成环境搭建、启动四套服务、验证核心功能
- **Verification**: `human-judgment`

## Open Questions
无
