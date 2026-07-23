# Tasks

- [x] Task 1: 创建 Webflux 全局响应包装 Advice
  - [x] SubTask 1.1: 新建 `WebfluxApiResponseAdvice.java`，实现 `ResponseBodyAdvice<Object>`，支持 Mono 解包后包装
  - [x] SubTask 1.2: 处理 String 类型手动 JSON 序列化
  - [x] SubTask 1.3: 处理 Void 类型包装
  - [x] SubTask 1.4: 已包装 ApiResponse 类型跳过重复包装

- [x] Task 2: 重构所有 Webflux Controller 返回类型
  - [x] SubTask 2.1: `AuthController` — `Mono<ApiResponse<T>>` → `Mono<T>`
  - [x] SubTask 2.2: `BillController` — `Mono<ApiResponse<T>>` → `Mono<T>`
  - [x] SubTask 2.3: `CategoryController` — `Mono<ApiResponse<T>>` → `Mono<T>`
  - [x] SubTask 2.4: `LedgerController` — `Mono<ApiResponse<T>>` → `Mono<T>`
  - [x] SubTask 2.5: `StatisticsController` — `Mono<ApiResponse<T>>` → `Mono<T>`

- [x] Task 3: 生成跨平台 Docker Compose 配置文件
  - [x] SubTask 3.1: 创建 `/docs/docker-compose/docker-compose-macos.yml`，保持现有 macOS 路径格式
  - [x] SubTask 3.2: 创建 `/docs/docker-compose/docker-compose-windows.yml`，改为 Windows 路径格式
  - [x] SubTask 3.3: 创建 `/docs/docker-compose/docker-compose-linux.yml`，改为 Linux 路径格式

- [x] Task 4: 编译验证与接口测试
  - [x] SubTask 4.1: `mvn clean compile` 全项目编译通过
  - [x] SubTask 4.2: 启动 Webflux 后端（8080），验证接口响应格式正常
  - [x] SubTask 4.3: 验证所有 Controller 接口返回正确（code=200 的 ApiResponse 包装）

# Task Dependencies
- Task 1 必须先完成，Task 2 依赖 Task 1
- Task 3 独立
- Task 4 depends on Task 1, 2, 3
