# Tasks

- [x] Task 1: 项目脚手架搭建与依赖配置
  - [x] SubTask 1.1: 使用 Maven 创建 Spring Boot 3.5.16 项目结构（parent + backend + frontend 模块）
  - [x] SubTask 1.2: 配置 Webflux、Security Reactive、R2DBC（MySQL）、Reactive MongoDB、Reactive Redis、JWT 等依赖
  - [x] SubTask 1.3: 配置 application.yml（数据库连接、Redis、MongoDB、JWT 密钥）
  - [x] SubTask 1.4: 初始化 Vue3 前端项目（vite + vue-router + pinia + axios + element-plus）

- [x] Task 2: 后端用户认证模块
  - [x] SubTask 2.1: 设计用户表结构（user 表：id, username, password, salt, created_at, updated_at），使用 R2DBC 实体映射
  - [x] SubTask 2.2: 实现用户注册接口（校验唯一性、密码加密存储）
  - [x] SubTask 2.3: 实现登录接口（Spring Security + JWT 生成 Token）
  - [x] SubTask 2.4: 实现 JWT 认证过滤器与 Security 配置
  - [x] SubTask 2.5: 实现 Token 刷新接口

- [x] Task 3: 后端记账与分类模块
  - [x] SubTask 3.1: 设计分类表（category 表：id, user_id, name, type, is_preset, created_at），使用 R2DBC 实体映射
  - [x] SubTask 3.2: 设计记账表（bill 表：id, user_id, category_id, amount, type, remark, bill_date, created_at, updated_at），使用 R2DBC 实体映射
  - [x] SubTask 3.3: 实现分类 CRUD 接口（R2DBC Repository + Mono/Flux）
  - [x] SubTask 3.4: 实现记账 CRUD 接口（R2DBC Repository + Mono/Flux，支持分页、筛选）

- [x] Task 4: 后端统计分析模块
  - [x] SubTask 4.1: 实现按周统计接口（聚合本周每日收支）
  - [x] SubTask 4.2: 实现按月统计接口（聚合当月每日收支及月总计）
  - [x] SubTask 4.3: 实现按年统计接口（聚合当年每月收支及年总计）
  - [x] SubTask 4.4: 实现按分类统计接口（各分类金额占比）
  - [x] SubTask 4.5: 对接 ReactiveRedisTemplate 缓存热点统计数据

- [x] Task 5: 前端基础框架与认证页面
  - [x] SubTask 5.1: 配置 Vue Router、Pinia、Axios 拦截器（自动携带 Token）
  - [x] SubTask 5.2: 实现登录页面
  - [x] SubTask 5.3: 实现注册页面
  - [x] SubTask 5.4: 实现路由守卫（未登录跳登录页）

- [x] Task 6: 前端记账与分类页面
  - [x] SubTask 6.1: 实现分类管理页面（增删改查）
  - [x] SubTask 6.2: 实现记账列表页面（分页、筛选、增删改查）
  - [x] SubTask 6.3: 实现新增/编辑记账弹窗

- [x] Task 7: 前端统计页面
  - [x] SubTask 7.1: 实现按周统计页面（柱状图/折线图展示每日收支）
  - [x] SubTask 7.2: 实现按月统计页面
  - [x] SubTask 7.3: 实现按年统计页面
  - [x] SubTask 7.4: 实现按分类统计页面（饼图展示占比）

- [x] Task 8: 系统联调与优化
  - [x] SubTask 8.1: 前后端接口联调
  - [x] SubTask 8.2: 统一异常处理与响应封装
  - [x] SubTask 8.3: 添加全局请求日志与参数校验
  - [x] SubTask 8.4: 基础性能测试与 Reactive Redis 缓存效果验证

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 3
- Task 5 depends on Task 1
- Task 6 depends on Task 3, Task 5
- Task 7 depends on Task 4, Task 6
- Task 8 depends on Task 7
