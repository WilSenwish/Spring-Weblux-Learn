# Checklist

## Webflux 全局响应包装 Advice
- [x] `ApiResponseWebFilter.java` 存在且实现 `WebFilter`，正确包装响应体
- [x] String 类型响应手动 JSON 序列化，避免类型转换异常
- [x] Void 类型正确包装为 `ApiResponse<Void>`
- [x] 已经是 `ApiResponse` 类型的响应跳过重复包装

## Controller 返回类型重构
- [x] `AuthController` 所有方法返回 `Mono<T>`（而非 `Mono<ApiResponse<T>>`）
- [x] `BillController` 所有方法返回 `Mono<T>`
- [x] `CategoryController` 所有方法返回 `Mono<T>`
- [x] `LedgerController` 所有方法返回 `Mono<T>`
- [x] `StatisticsController` 所有方法返回 `Mono<T>`

## Docker Compose 配置文件
- [x] `/docs/docker-compose/docker-compose-macos.yml` 存在且路径为 macOS 格式
- [x] `/docs/docker-compose/docker-compose-windows.yml` 存在且路径为 Windows 格式
- [x] `/docs/docker-compose/docker-compose-linux.yml` 存在且路径为 Linux 格式
- [x] 三套配置文件均包含 MySQL 8.4.6、Redis Stack 7.2.0-v10、MongoDB 7.0.24 服务定义
- [x] 三套配置均使用统一的 localservice_network 网络

## 编译与接口验证
- [x] `mvn clean compile` 全项目编译通过
- [x] Webflux 后端在 8080 端口正常启动
- [x] 登录接口返回 `{"code":200,"message":"success","data":{...}}` 格式
- [x] 账单列表接口返回正确的 ApiResponse 包装格式
- [x] 分类列表接口返回正确的 ApiResponse 包装格式
- [x] 账本列表接口返回正确的 ApiResponse 包装格式
