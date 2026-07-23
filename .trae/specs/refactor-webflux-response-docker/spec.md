# Webflux 响应层重构 + 跨平台 Docker 配置 Spec

## Why
1. 当前 Webflux Controller 直接返回 `Mono<ApiResponse<T>>`，与 WebMVC 通过全局 Advice 统一包装的模式不一致，Controller 层承担了响应格式封装职责。
2. 项目缺少跨平台 Docker Compose 配置文件，开发和部署时需要手动适配不同操作系统（Mac/Windows/Linux）的卷路径。

## What Changes
- Webflux 所有 Controller 接口返回类型从 `Mono<ApiResponse<T>>` 改为 `Mono<T>`（或 `Mono<PageResult<T>>` 等原始类型），由全局 Advice 统一包装为 `Mono<ApiResponse<T>>`
- 新增 `WebfluxApiResponseAdvice`（`@ControllerAdvice` + `ResponseBodyAdvice`），解包 Mono 后包装 ApiResponse
- 在 `/docs/docker-compose` 目录下生成 3 套 Docker Compose 配置文件（docker-compose-macos.yml / docker-compose-windows.yml / docker-compose-linux.yml），使用各自平台的路径格式

## Impact
- 受影响模块: `accounting-webflux-backend`
- 受影响代码: 所有 Webflux Controller 类、新增 Advice 类
- 新增文档: `/docs/docker-compose/docker-compose-macos.yml`、`/docs/docker-compose/docker-compose-windows.yml`、`/docs/docker-compose/docker-compose-linux.yml`

## ADDED Requirements

### Requirement: Webflux 全局响应包装
Webflux Controller 方法返回原始类型（`Mono<T>`、`Mono<PageResult<T>>`、`Mono<String>`、`Mono<Void>`），由全局 Advice 自动包装为 `Mono<ApiResponse<T>>`。

#### Scenario: Controller 返回 Mono<Bill>
- **WHEN** Controller 方法返回 `Mono<Bill>`
- **THEN** Advice 拦截后包装为 `Mono<ApiResponse<Bill>>`

#### Scenario: Controller 返回 Mono<String>
- **WHEN** Controller 方法返回 `Mono<String>`
- **THEN** Advice 包装为 `ApiResponse<String>` 后手动 JSON 序列化（避免 StringHttpMessageConverter 异常）

#### Scenario: Controller 返回 Mono<Void>
- **WHEN** Controller 方法返回 `Mono<Void>`
- **THEN** Advice 包装为 `Mono<ApiResponse<Void>>`

#### Scenario: 已经是 ApiResponse 的不重复包装
- **WHEN** 返回值已经是 `ApiResponse` 类型
- **THEN** Advice 直接返回，不重复包装

---

### Requirement: 跨平台 Docker Compose 配置
依据现有 docker-compose.yml 内容，生成适配 3 个操作系统的配置文件。

#### 路径映射规则

| 平台 | 基础路径 | 说明 |
|------|---------|------|
| macOS | `/Users/chenjunbing/Develop/LocalService` | 保持现有格式（Docker Desktop for Mac 直接支持） |
| Windows | `C:\Users\chenjunbing\Develop\LocalService` | Docker Desktop for Windows 路径格式 |
| Linux | `/home/chenjunbing/Develop/LocalService` | Linux 用户家目录路径格式 |

#### Scenario: macOS 版本直接使用
- **WHEN** 在 macOS 上执行 `docker compose -f docker-compose-macos.yml up -d`
- **THEN** MySQL/Redis/MongoDB 容器正常启动，数据卷正确挂载

#### Scenario: Windows 版本直接使用
- **WHEN** 在 Windows 上执行 `docker compose -f docker-compose-windows.yml up -d`
- **THEN** 容器正常启动，数据卷正确挂载

#### Scenario: Linux 版本直接使用
- **WHEN** 在 Linux 上执行 `docker compose -f docker-compose-linux.yml up -d`
- **THEN** 容器正常启动，数据卷正确挂载
