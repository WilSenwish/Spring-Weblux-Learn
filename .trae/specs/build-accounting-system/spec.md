# 记账系统 Spec

## Why
需要一个支持多用户、多维度统计的在线记账系统，帮助用户记录日常收支并按周/月/年/分类进行统计分析，以便掌握财务状况。

## What Changes
- 构建后端服务：Spring Boot 3.5.16 + Spring Webflux（全异步响应式） + Spring Security Reactive + Redis（Reactive） + MySQL（R2DBC） + MongoDB（Reactive）
- 构建前端应用：Vue3 单页应用
- 实现用户认证体系（注册、登录、JWT Token）
- 实现记账 CRUD（收入/支出记录）
- 实现多维度统计接口（按周、按月、按年、按分类）
- 使用 MySQL（R2DBC）存储用户、账单、分类等结构化数据
- 使用 MongoDB（Reactive）存储统计快照或日志等非结构化数据
- 使用 Redis（ReactiveRedisTemplate）缓存用户会话及热点统计数据
- **基线约束**：后端全链路基于 Reactor（Mono/Flux），禁止任何阻塞调用（如 block()、JDBC、JPA、阻塞式 Redis/MongoDB 驱动）

## Impact
- Affected specs: 用户认证、记账管理、统计分析
- Affected code: 后端全部模块、前端全部模块

## ADDED Requirements

### Requirement: 用户认证模块
The system SHALL 提供用户注册与登录能力，基于 Spring Security + JWT 实现无状态认证。

#### Scenario: 用户注册
- **WHEN** 用户提交用户名、密码、确认密码
- **THEN** 系统校验唯一性后创建用户，返回注册成功

#### Scenario: 用户登录
- **WHEN** 用户提交用户名和密码
- **THEN** 校验通过后返回 JWT Token，前端后续请求携带 Token

#### Scenario: Token 刷新
- **WHEN** Token 即将过期
- **THEN** 用户可通过刷新接口获取新 Token

### Requirement: 记账管理模块
The system SHALL 提供收支记录的增删改查能力，每条记录包含金额、类型（收入/支出）、分类、备注、时间。

#### Scenario: 新增记账
- **WHEN** 用户提交记账信息
- **THEN** 系统保存记录并返回成功

#### Scenario: 查询记账列表
- **WHEN** 用户按时间范围、类型、分类筛选
- **THEN** 系统返回分页结果

#### Scenario: 修改/删除记账
- **WHEN** 用户修改或删除自己的记账记录
- **THEN** 系统更新或删除对应记录

### Requirement: 分类管理模块
The system SHALL 提供收支分类的自定义能力，支持系统预设分类和用户自定义分类。

#### Scenario: 查询分类
- **WHEN** 用户获取分类列表
- **THEN** 系统返回收入/支出分类列表

#### Scenario: 自定义分类
- **WHEN** 用户新增、修改、删除自定义分类
- **THEN** 系统相应处理，预设分类不可删除

### Requirement: 统计分析模块
The system SHALL 提供按周、按月、按年、按分类的收支统计能力。

#### Scenario: 按周统计
- **WHEN** 用户选择周统计
- **THEN** 系统返回本周每日收支汇总

#### Scenario: 按月统计
- **WHEN** 用户选择月统计
- **THEN** 系统返回当月每日收支汇总及月总计

#### Scenario: 按年统计
- **WHEN** 用户选择年统计
- **THEN** 系统返回当年每月收支汇总及年总计

#### Scenario: 按分类统计
- **WHEN** 用户选择分类统计
- **THEN** 系统返回各分类的金额占比及明细

### Requirement: 数据存储策略（全响应式）
The system SHALL 使用 MySQL（R2DBC）存储核心业务数据（用户、账单、分类），使用 MongoDB（Spring Data MongoDB Reactive）存储统计快照或日志等非结构化数据，使用 Redis（ReactiveRedisTemplate）缓存会话及热点统计。所有数据访问层必须返回 Mono/Flux，禁止阻塞。

#### Scenario: 缓存加速
- **WHEN** 用户请求统计数据
- **THEN** 优先通过 ReactiveRedisTemplate 读取 Redis 缓存，未命中时通过 R2DBC/Reactive MongoDB 计算并异步写入缓存

#### Scenario: 全异步链路
- **WHEN** 任何请求进入系统
- **THEN** Controller → Service → Repository 全链路返回 Mono/Flux，线程模型基于 Netty EventLoop，禁止调用 block() 或阻塞式 IO

## MODIFIED Requirements
无

## REMOVED Requirements
无
