# Webflux 对照学习指南

> 本项目的最大特色是「双栈同业务」：同一套记账系统（个人/共享账本、收支记账、分类管理、统计分析）同时存在两套实现 —— 基于 **Spring WebMVC + MyBatis** 的命令式阻塞版本，以及基于 **Spring Webflux + R2DBC + Reactive MongoDB** 的响应式非阻塞版本。读者通过对照阅读两套实现，可以系统地理解响应式编程与命令式编程的差异。

- WebMVC 后端模块：`accounting-webmvc-backend`
- Webflux 后端模块：`accounting-webflux-backend`
- 共享模块（DTO/Entity/ApiResponse/PageResult/异常/JWT 等）：`accounting-common`

---

## 1. 概述

### 1.1 项目双栈设计目的（学习价值）

| 目的 | 说明 |
| --- | --- |
| 对照学习 | 同一份业务（账单 CRUD、统计、登录、权限）在两条技术栈下分别实现，方便逐行对照。 |
| 真实工程化 | 不是 hello-world 级别的 demo，而是带 JWT 鉴权、MySQL + MongoDB + Redis 三存储、事务、全局响应包装的完整业务。 |
| 渐进式改造参考 | 真实项目里"WebMVC 改造 Webflux"该如何取舍，本项目就是一份可参照的样本。 |
| 易用工具链 | 全部使用 Spring Boot 3.5.16 + JDK 17，依赖与配置按生产实践组织（详见各模块 `pom.xml`）。 |

### 1.2 适用范围

- **适合**已经熟悉 Spring WebMVC、MyBatis、Spring Security 同步栈的开发者。
- **不适合**完全没接触过 Spring Web 的初学者（请先掌握 WebMVC 再来）。
- 不要求预先了解响应式编程，但建议带着"为什么不用同步写法"的疑问来阅读。

### 1.3 推荐阅读顺序

1. **先看 WebMVC**：理解业务、字段、接口、权限模型。
2. **再看 Webflux**：同样一份业务用响应式重新表达时，**类型（Mono/Flux）、链式操作、错误传播**都发生了什么变化。
3. **最后对比公共差异**：异常处理、全局响应包装、过滤器链、事务等。

---

## 2. 核心概念对比表

| 维度 | WebMVC | Webflux |
| --- | --- | --- |
| 容器 | Tomcat Servlet | Netty（NIO） |
| 线程模型 | 每个请求 1 个 Servlet 线程（阻塞等待 I/O） | EventLoop 线程复用（非阻塞） |
| Controller 返回值 | `Xxx` / `List<Xxx>` / `PageResult<Xxx>` | `Mono<Xxx>` / `Flux<Xxx>` / `Mono<PageResult<Xxx>>` |
| MySQL 访问 | MyBatis Mapper（同步阻塞） | Spring Data R2DBC（`ReactiveCrudRepository` + `R2dbcEntityTemplate`） |
| MongoDB | `MongoTemplate`（同步） | `ReactiveMongoTemplate` / `ReactiveMongoRepository`（响应式） |
| Redis | `RedisTemplate`（同步） | `ReactiveRedisTemplate`（响应式） |
| 异常处理 | `@ControllerAdvice` + `@ExceptionHandler`（同步返回 `ApiResponse`） | 同上，但**所有方法返回 `Mono<ApiResponse<?>>`** |
| 全局响应包装 | `ResponseBodyAdvice` | `WebFilter` + `ServerHttpResponseDecorator`（本项目做法） |
| 鉴权过滤器 | `OncePerRequestFilter`（Servlet Filter 链） | `WebFilter` + `ReactiveSecurityContextHolder` |
| 事务 | `@Transactional`（AOP 切面，命令式） | R2DBC 事务（编程式 `TransactionalOperator` 或 `TransactionCallback`） |
| 分页 | PageHelper（`PageHelper.startPage` + `PageInfo`） | 自定义 `Mono<PageResult>`（基于 `R2dbcEntityTemplate` 手写 count + list） |
| 上下文获取 | `SecurityContextHolder.getContext()`（ThreadLocal） | `ReactiveSecurityContextHolder.getContext()`（响应式上下文） |
| 测试 | `@SpringBootTest` + MockMvc | `@SpringBootTest` + `WebTestClient` / `reactor-test` 的 `StepVerifier` |

> 提示：本项目 Webflux 模块当前并未启用 R2DBC 编程式事务（`@Transactional` 在响应式下不生效），`BillService` 内的多步操作依赖"链式组合 + 错误传播"保证最终一致性；如需强事务，请参考第 4 节"事务处理"。

---

## 3. 典型场景对照示例

> 以下示例均来自本项目实际代码，并按"WebMVC vs Webflux"成对呈现，便于对照阅读。

### 场景 1：单条数据查询（Controller 入口）

#### WebMVC —— `accounting-webmvc-backend/.../controller/BillController.java`

```java
@RestController
@RequestMapping("/api/bills")
public class BillController extends BaseController {

    @Autowired
    private BillService billService;

    @PostMapping
    public Bill create(@Valid @RequestBody BillRequest request) {
        Long userId = getCurrentUserId();   // 同步获取
        return billService.createBill(userId, request);   // 同步返回
    }
}
```

`BaseController.getCurrentUserId()` 走的是 ThreadLocal 风格的 `SecurityContextHolder`（见 `accounting-webmvc-backend/.../controller/BaseController.java`）。

#### Webflux —— `accounting-webflux-backend/.../controller/BillController.java`

```java
@RestController
@RequestMapping("/api/bills")
public class BillController extends BaseController {

    @Autowired
    private BillService billService;

    @PostMapping
    public Mono<Bill> create(@Valid @RequestBody BillRequest request) {
        return getCurrentUserId()                 // 返回 Mono<Long>
                .flatMap(userId -> billService.createBill(userId, request));
    }
}
```

`Webflux` 的 `BaseController.getCurrentUserId()` 拿的是响应式上下文：

```java
// accounting-webflux-backend/.../controller/BaseController.java
protected Mono<Long> getCurrentUserId() {
    return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication().getName())
            .flatMap(username -> userRepository.findByUsername(username))
            .map(User::getId);
}
```

**关键差异**：

- WebMVC：`getCurrentUserId()` 同步阻塞返回 `Long`，后续直接 `return billService.create(...)`。
- Webflux：`getCurrentUserId()` 返回 `Mono<Long>`，必须用 `flatMap` 串联下游，整个链路是 `Mono` / `Flux` 的拼装。

---

### 场景 2：列表 + 多条件分页查询

#### WebMVC —— `accounting-webmvc-backend/.../service/BillService.java`

```java
public PageResult<Bill> listBills(Long userId, BillQueryRequest query) {
    // 1. PageHelper 拦截下一次查询，自动拼接 limit/offset + count(*)
    PageHelper.startPage(query.getPage(), query.getSize());
    // 2. 同步 SQL，参数通过 Mapper XML 拼接
    List<Bill> bills = billMapper.findByUserId(
            userId,
            query.getType(),
            query.getCategoryId(),
            query.getLedgerId(),
            query.getStartDate(),
            query.getEndDate()
    );
    // 3. PageInfo 封装总数、当前页等
    PageInfo<Bill> pageInfo = new PageInfo<>(bills);
    return PageResult.<Bill>builder()
            .list(pageInfo.getList())
            .total(pageInfo.getTotal())
            .page(query.getPage())
            .size(query.getSize())
            .build();
}
```

对应的 MyBatis 动态 SQL（`accounting-webmvc-backend/src/main/resources/mapper/BillMapper.xml`）：

```xml
<select id="findByUserId" resultMap="BillResultMap">
    SELECT * FROM bill
    WHERE user_id = #{userId}
    <if test="type != null">AND type = #{type}</if>
    <if test="categoryId != null">AND category_id = #{categoryId}</if>
    <if test="ledgerId != null">AND ledger_id = #{ledgerId}</if>
    <if test="startDate != null and startDate != ''">AND bill_date &gt;= #{startDate}</if>
    <if test="endDate != null and endDate != ''">AND bill_date &lt;= #{endDate}</if>
    ORDER BY bill_date DESC
</select>
```

#### Webflux —— `accounting-webflux-backend/.../service/BillService.java`

```java
public Mono<PageResult<Bill>> listBills(Long userId, BillQueryRequest query) {
    // 1. 用 Spring Data 关系型条件 API 拼装动态条件
    Criteria criteria = Criteria.where("userId").is(userId);
    if (query.getType() != null)        criteria = criteria.and("type").is(query.getType());
    if (query.getCategoryId() != null)  criteria = criteria.and("categoryId").is(query.getCategoryId());
    if (query.getLedgerId() != null)    criteria = criteria.and("ledgerId").is(query.getLedgerId());
    if (query.getStartDate() != null)   criteria = criteria.and("billDate").greaterThanOrEquals(query.getStartDate());
    if (query.getEndDate() != null)     criteria = criteria.and("billDate").lessThanOrEquals(query.getEndDate());

    Query listQuery = Query.query(criteria)
            .sort(Sort.by(Sort.Direction.DESC, "billDate"))
            .offset((long) (query.getPage() - 1) * query.getSize())
            .limit(query.getSize());

    // 2. 列表查询 + 计数查询并发执行
    Mono<List<Bill>> listMono = template.select(Bill.class).matching(listQuery).all().collectList();
    Mono<Long>       countMono = template.select(Bill.class).matching(Query.query(criteria)).count();

    // 3. Mono.zip 等两个结果都到位后再组装
    return Mono.zip(listMono, countMono)
            .map(tuple -> PageResult.<Bill>builder()
                    .list(tuple.getT1())
                    .total(tuple.getT2())
                    .page(query.getPage())
                    .size(query.getSize())
                    .build());
}
```

对应的 R2DBC Repository（`accounting-webflux-backend/.../repository/BillRepository.java`）只放最简单的方法，复杂查询走 `R2dbcEntityTemplate`：

```java
public interface BillRepository extends ReactiveCrudRepository<Bill, Long> {
    Flux<Bill> findByUserId(Long userId);
}
```

**关键差异**：

| 维度 | WebMVC | Webflux |
| --- | --- | --- |
| 分页方式 | PageHelper ThreadLocal 拦截 | `Mono.zip(list, count)` 自行组合 |
| 动态条件 | MyBatis XML `<if>` | Spring Data `Criteria` 流式 API |
| 底层驱动 | JDBC 阻塞 | R2DBC 非阻塞 |
| 并发 | 单线程 | `Mono.zip` 自动并发（list + count 并行） |

---

### 场景 3：创建账单并同步写入 MySQL + MongoDB + Redis

#### WebMVC —— `accounting-webmvc-backend/.../service/BillService.java`

```java
@Transactional   // 同一线程，事务生效
public Bill createBill(Long userId, BillRequest request) {
    Category category = categoryMapper.findById(request.getCategoryId());
    if (category == null) {
        throw new BusinessException(400, "分类不存在");
    }
    // ... 业务校验略 ...

    // 1. 写 MySQL（同步阻塞）
    Bill bill = Bill.builder() /* ... */ .build();
    billMapper.insert(bill);

    // 2. 同步写 MongoDB（同步阻塞）
    BillDocument document = BillDocument.builder().mysqlId(bill.getId()) /* ... */ .build();
    billDocumentRepository.save(document);

    return bill;
}
```

#### Webflux —— `accounting-webflux-backend/.../service/BillService.java`

```java
public Mono<Bill> createBill(Long userId, BillRequest request) {
    return categoryRepository.findById(request.getCategoryId())
            // switchIfEmpty：数据库无记录时，链上抛业务异常
            .switchIfEmpty(Mono.error(new BusinessException(400, "分类不存在")))
            .flatMap(category -> {
                if (!userId.equals(category.getUserId()) && !Integer.valueOf(1).equals(category.getIsPreset())) {
                    return Mono.error(new BusinessException("无权使用该分类"));
                }
                // ... 业务校验略 ...
                return resolveLedgerId(userId, request.getLedgerId());   // 链上继续
            })
            .flatMap(ledgerId -> {
                Bill bill = Bill.builder() /* ... */ .build();
                // 1. 写 MySQL（R2DBC save）
                return billRepository.save(bill)
                        // 2. 再写 MongoDB，写完返回 bill
                        .flatMap(savedBill -> {
                            BillDocument document = BillDocument.builder()
                                    .mysqlId(savedBill.getId())
                                    /* ... */
                                    .build();
                            return billDocumentRepository.save(document)
                                    .thenReturn(savedBill);
                        });
            });
}
```

**关键差异**：

- WebMVC：自上而下顺序执行，`@Transactional` 整体包裹。
- Webflux：`Mono` / `Flux` 链式组合，每一步可以加 `.flatMap` / `.switchIfEmpty` / `.onErrorResume`。
- 异常不再靠 `try-catch` / `if-null-throw`，而是 `Mono.error(new BusinessException(...))` 直接注入到响应式流中，由上游 `onErrorResume` 或全局异常处理器统一收口。

> 备注：本项目 Webflux 版本不依赖 MongoDB 启动后同步（未提供 `MongoSyncRunner`）。如需要"启动时把 MySQL 账单同步到 MongoDB"，参考第 5 节"学习路径"。

---

### 场景 4：事务处理

#### WebMVC —— `@Transactional`（AOP 切面，ThreadLocal 绑定连接）

```java
@Transactional
public Bill createBill(Long userId, BillRequest request) {
    // 多步 SQL 默认在同一事务
    billMapper.insert(bill);
    // ...
}
```

#### Webflux —— 编程式 R2DBC 事务（推荐）

响应式下 `@Transactional` 不会生效（切面基于 ThreadLocal，而响应式跨线程），需要使用 `TransactionalOperator`（`org.springframework.transaction.reactive.TransactionalOperator`）包裹：

```java
@Service
public class BillService {

    @Autowired
    private TransactionalOperator txOperator;   // 由 R2dbcConfig 注入 ConnectionFactory 时构建

    public Mono<Bill> createBillTx(Long userId, BillRequest request) {
        return txOperator.execute(status ->
                categoryRepository.findById(request.getCategoryId())
                        .flatMap(category -> billRepository.save(/* ... */))
                        .flatMap(bill -> billDocumentRepository.save(/* ... */).thenReturn(bill))
        );
    }
}
```

> 说明：本项目当前 Webflux 模块**未启用 R2DBC 事务**（链式 `save` 默认单语句提交），依赖响应式流的"任一环节失败即整体中断"特性获得"准事务"语义。如果业务要求强一致，请在 `R2dbcConfig` 注入 `R2dbcTransactionManager` 并提供 `TransactionalOperator` Bean，再在 Service 层用 `txOperator.execute(...)` 包裹。

#### Redis 缓存穿透对照（`StatisticsService`）

WebMVC 走 `RedisTemplate.get/set`：

```java
// accounting-webmvc-backend/.../service/StatisticsService.java
String cached = redisTemplate.opsForValue().get(key);
if (cached != null) {
    return objectMapper.readValue(cached, StatisticsResponse.class);
}
StatisticsResponse response = calculateWeeklyStats(userId, ledgerId);
cacheResponse(key, response);
return response;
```

Webflux 走 `ReactiveRedisTemplate` + `switchIfEmpty`：

```java
// accounting-webflux-backend/.../service/StatisticsService.java
return redisTemplate.opsForValue().get(key)
        .flatMap(json -> {
            try {
                return Mono.just(objectMapper.readValue(json, StatisticsResponse.class));
            } catch (Exception e) {
                return Mono.empty();
            }
        })
        .switchIfEmpty(
                calculateWeeklyStats(userId, ledgerId)
                        .flatMap(response -> cacheResponse(key, response).thenReturn(response))
        );
```

**关键差异**：

- 同步：`if (cached != null) return ... else ...` 显式判断。
- 响应式：`switchIfEmpty(从 DB 查)` 表达"缓存空时回源"，语义等价但完全非阻塞。

---

### 场景 5：JWT 认证过滤器

#### WebMVC —— `accounting-webmvc-backend/.../security/JwtAuthenticationFilter.java`

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtUtil.validateToken(token, userDetails.getUsername())) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

要点：`OncePerRequestFilter` 是 Servlet 同步过滤器；通过 `SecurityContextHolder`（ThreadLocal）传递认证信息。

#### Webflux —— `accounting-webflux-backend/.../security/JwtAuthenticationFilter.java`

```java
@Component
public class JwtAuthenticationFilter implements WebFilter {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private ReactiveUserDetailsService userDetailsService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String token = authHeader.substring(7);
        return jwtUtil.extractUsernameReactive(token)
                .flatMap(username -> jwtUtil.validateTokenReactive(token, username)
                        .flatMap(valid -> {
                            if (Boolean.TRUE.equals(valid)) {
                                return userDetailsService.findByUsername(username)
                                        .flatMap(userDetails -> {
                                            UsernamePasswordAuthenticationToken authentication =
                                                    new UsernamePasswordAuthenticationToken(
                                                            userDetails, null, userDetails.getAuthorities());
                                            // 关键：通过 contextWrite 将认证信息写入响应式上下文
                                            return chain.filter(exchange)
                                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                                        });
                            }
                            return chain.filter(exchange);
                        }))
                .switchIfEmpty(chain.filter(exchange))
                .onErrorResume(throwable -> chain.filter(exchange));
    }
}
```

要点：

- 实现的是 `WebFilter`，整个方法返回 `Mono<Void>`。
- 用 `ReactiveSecurityContextHolder.withAuthentication(authentication).contextWrite(...)` 把认证信息**写入响应式上下文**（沿调用链向下游传递，不依赖 ThreadLocal）。
- 错误处理靠 `onErrorResume` / `switchIfEmpty`，没有 `try-catch`。

---

## 4. 关键差异（必读）

### 4.1 阻塞点识别（**最常踩的坑**）

Webflux 之所以能扛高并发，是因为请求处理链路上**没有任何同步阻塞**。一旦在响应式链中调用了 `.block()` / `.blockFirst()` / `.blockLast()`，就退化成了同步模型。

**错误示例（不要这样写）**：

```java
// 反例：在 Controller 中调用 .block()
@GetMapping("/{id}")
public Bill getById(@PathVariable Long id) {
    return billRepository.findById(id).block();   // ❌ 阻塞 EventLoop 线程
}
```

**正确写法**：

```java
@GetMapping("/{id}")
public Mono<Bill> getById(@PathVariable Long id) {
    return billRepository.findById(id);   // ✅ 返回 Mono，由框架调度
}
```

**允许阻塞的场景**：

- `ApplicationRunner` / `CommandLineRunner` / `CommandLineRunner` 的 `run` 方法本身是同步方法。
- 启动期一次性预热（`@PostConstruct` 内的初始化）：可以 `.block()` 或 `.subscribe()`。
- 单元测试中（`reactor-test` 的 `StepVerifier.create(mono).expectNext(...).verifyComplete()`）。

> 本项目目前未在业务运行时调用 `.block()`，可在 IDE 全文搜索 `\.block\(` 验证。

---

### 4.2 错误传播

| 风格 | WebMVC | Webflux |
| --- | --- | --- |
| 同步 | `try-catch` / 抛 `BusinessException` 由 `@ExceptionHandler` 统一收 | （不推荐）`try-catch` 包响应式代码会破坏流 |
| 全局异常 | `@RestControllerAdvice` + `@ExceptionHandler` 返回 `ApiResponse` | 同上，但方法返回 `Mono<ApiResponse<?>>`（见 `accounting-webflux-backend/.../common/GlobalExceptionHandler.java`） |
| 链上错误 | 无（异常向上抛） | `onErrorResume` / `onErrorReturn` / `doOnError` / `Mono.error(...)` |

**Webflux 全局异常示例**（`accounting-webflux-backend/.../common/GlobalExceptionHandler.java`）：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Mono<ApiResponse<?>> handleBusinessException(BusinessException ex) {
        return Mono.just(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ApiResponse<?>> handleWebExchangeBindException(WebExchangeBindException ex) {
        String message = ex.getAllErrors().stream()
                .findFirst()
                .map(ObjectError::getDefaultMessage)
                .orElse("请求参数错误");
        return Mono.just(ApiResponse.error(400, message));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ApiResponse<?>> handleException(Exception ex) {
        return Mono.just(ApiResponse.error(500, "系统内部错误: " + ex.getMessage()));
    }
}
```

**链上错误处理示例**（`accounting-webflux-backend/.../service/BillService.java`）：

```java
return billRepository.findById(billId)
        .switchIfEmpty(Mono.error(new BusinessException(404, "账单不存在或无权限")))   // 空结果转错误
        .flatMap(bill -> checkBillEditPermission(userId, bill)
                .flatMap(allowed -> allowed
                        ? billRepository.save(bill)
                        : Mono.error(new BusinessException(403, "无权修改他人账单"))));
```

---

### 4.3 线程模型

| 维度 | WebMVC | Webflux |
| --- | --- | --- |
| 默认容器 | Tomcat（Servlet 3.1） | Netty |
| 工作线程 | `tomcat-threads`（默认 200） | `reactor-http-nio-`（CPU 核心数 × 2） |
| 线程类型 | Servlet 线程（一个请求一个） | EventLoop 线程（少量，复用，处理 I/O 事件） |
| 阻塞代价 | 请求线程挂起直到 I/O 完成 | **绝对不能阻塞**，否则整个 EventLoop 被卡住 |
| 调度切换 | 业务线程 = Servlet 线程 | 通过 `Schedulers.boundedElastic()` 把阻塞任务（JDBC、阻塞 Redis 等）丢到独立线程池 |

> 本项目 Webflux 后端所有持久化都是真·响应式（R2DBC / Reactive MongoDB / Reactive Redis），因此不需要 `boundedElastic` 转异步。

---

### 4.4 背压（Backpressure）

| 维度 | WebMVC | Webflux |
| --- | --- | --- |
| 数据形态 | `List<Xxx>` 一次拿全 | `Flux<Xxx>` 流式推送 |
| 背压 | 无（内存里一次性塞满） | 自动（基于 Reactive Streams 规范：Subscriber 告诉 Publisher "慢点/我只能要 N 个"） |
| 类比 | HTTP 请求-响应（一来一回） | 订报纸（你订几份就送几份） |

例如，调用一个分页接口 10000 条数据：

- WebMVC：客户端需要等服务器全部查询完毕再一次性返回。
- Webflux：服务器可以流式 `Flux<Xxx>` 一条一条 `onNext`，客户端可以 `request(n)` 告诉服务端"先给我 100 条"。

**响应式下的写法**：

```java
@GetMapping(value = "/stream", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
public Flux<Bill> stream() {
    return billRepository.findAll();   // 流式
}
```

> 本项目目前未提供 `Flux` 流式接口，但 `StatisticsService` 内部已用 `Flux<TimePeriodStat>.collectList()` 演示了 `Flux → List` 的转换。

---

## 5. 学习路径建议

针对**已经熟悉 WebMVC** 的 Java 开发者，建议按以下顺序循序渐进：

### 第一阶段：理解业务（WebMVC 视角）

阅读 `accounting-webmvc-backend` 的下列文件，**先搞懂业务**：

- `controller/` —— 接口路径、请求/响应结构
- `service/BillService.java` —— 业务校验、事务、多存储写入
- `service/StatisticsService.java` —— Redis 缓存 + MySQL 聚合
- `mapper/BillMapper.xml` + `mapper/BillMapper.java` —— 动态 SQL 写法
- `security/JwtAuthenticationFilter.java` —— 鉴权流程

### 第二阶段：理解响应式语义（Webflux 视角）

带着"这段同步代码用 Mono/Flux 该怎么写？"的疑问，对照阅读：

- `controller/BillController.java` —— `Mono<Xxx>` 返回值
- `service/BillService.java` —— `.flatMap` / `.switchIfEmpty` / `Mono.error`
- `service/StatisticsService.java` —— `Mono.zip` 组合 list+count；`switchIfEmpty` 实现缓存穿透保护
- `repository/BillRepository.java` —— `ReactiveCrudRepository`

**对照阅读技巧**：把两个模块的 `BillService.listBills` 并排打开，会发现响应式版本里 *没有 `PageHelper`*，而是手写 `Mono.zip(list, count)`。这是 Webflux 学习的"第一道门"。

### 第三阶段：理解鉴权与上下文

阅读：

- `security/JwtAuthenticationFilter.java` —— `WebFilter` + `ReactiveSecurityContextHolder`
- `controller/BaseController.java` —— 响应式上下文拿当前用户
- 对照 `accounting-webmvc-backend/.../security/JwtAuthenticationFilter.java`

关注：

- WebMVC：`SecurityContextHolder`（ThreadLocal）
- Webflux：`ReactiveSecurityContextHolder` + `contextWrite(...)`（响应式上下文）

### 第四阶段：理解全局响应包装

阅读：

- WebMVC：`accounting-webmvc-backend/.../config/ApiResponseAdvice.java` —— `ResponseBodyAdvice` 在序列化前包一层 `ApiResponse`。
- Webflux：`accounting-webflux-backend/.../config/ApiResponseWebFilter.java` —— `WebFilter` + `ServerHttpResponseDecorator` 在写出响应前把 `DataBuffer` 重新包裹。

> 因为响应式下"返回值是 `Mono<T>`"、写回时机是 `writeWith(Publisher<DataBuffer>)`，所以不能像 WebMVC 那样用 `ResponseBodyAdvice` 截胡。Webflux 只能在 `WebFilter` 里装饰 `ServerHttpResponse`，等下游 `Mono` 全部 `onNext` 完毕、`DataBufferUtils.join` 拿到完整字节再包装。

### 第五阶段：实战

试着把一个 WebMVC Controller 改写成 Webflux：

1. 把所有方法返回值改为 `Mono<Xxx>` / `Flux<Xxx>`。
2. 把 `xxxMapper.xxx(...)` 替换为 `xxxRepository.xxx(...)` 或 `R2dbcEntityTemplate`。
3. 把 `if (xxx == null) throw ...` 改为 `.switchIfEmpty(Mono.error(...))`。
4. 把 `if (cached != null) ... else ...` 改为 `.switchIfEmpty(...)`。
5. 启动应用，确保没有任何 `.block()` 调用残留在 Controller/Service。

---

## 6. 何时该用 Webflux，何时该用 WebMVC

### 6.1 适合 Webflux 的场景

- **高并发 I/O 密集型应用**：API 网关、反向代理、消息推送、长连接服务。
- **大量 WebSocket / SSE 实时通信**。
- **微服务之间大量远程调用**（每个调用都是一次网络 I/O，Webflux 收益最大）。
- **资源受限**（容器化部署下想用更少线程数扛住更多连接）。

### 6.2 适合 WebMVC 的场景

- **CRUD 为主的业务系统**（本项目记账系统其实是这种），用 WebMVC 更直观。
- **团队对响应式编程不熟悉**：响应式代码一旦写错（漏写 `.flatMap` 而写成 `.map`、或者在响应式链里偷偷阻塞），debug 成本远高于 WebMVC。
- **依赖了大量阻塞中间件**（如老版本 JDBC 驱动、阻塞 Redis 客户端、阻塞 HTTP 客户端等）—— 这些中间件一旦接入，Webflux 反而需要 `.subscribeOn(Schedulers.boundedElastic())` 退化使用，复杂度上升但收益下降。
- **事务模型复杂**：跨多个 SQL 的强事务在 Webflux 下需要 `TransactionalOperator`，心智负担更重。

### 6.3 原则

> **不要为了响应式而响应式**。

如果业务主要是 CRUD、没有高并发 I/O 需求、Webflux 不会带来数量级的提升，**WebMVC 更简单、更易维护、更易招聘**。

本项目刻意保留两套实现，目的是让你**有能力判断**：

- 当下这套业务，应该选哪一套？
- 如果未来要切换，**哪些点最容易踩坑**？

---

## 7. 一页速查

```text
# WebMVC
Controller  : @RestController,  return T
Service     : @Transactional,  if (x == null) throw new BusinessException(...)
Mapper      : MyBatis,         同步 SQL
Redis       : RedisTemplate,   同步 GET/SET
MongoDB     : MongoTemplate,   同步 save
Filter      : OncePerRequestFilter, SecurityContextHolder (ThreadLocal)
事务        : @Transactional (AOP + ThreadLocal)
分页        : PageHelper.startPage + PageInfo
全局响应包装: ResponseBodyAdvice
测试        : MockMvc

# Webflux
Controller  : @RestController,  return Mono<T> / Flux<T>
Service     : Mono.error / switchIfEmpty / onErrorResume / Mono.zip
Repository  : ReactiveCrudRepository / R2dbcEntityTemplate
Redis       : ReactiveRedisTemplate,  switchIfEmpty 缓存穿透
MongoDB     : ReactiveMongoRepository / ReactiveMongoTemplate
Filter      : WebFilter, ReactiveSecurityContextHolder (响应式上下文)
事务        : TransactionalOperator.execute(status -> ...)
分页        : Mono.zip(list, count) → Mono<PageResult>
全局响应包装: WebFilter + ServerHttpResponseDecorator + DataBufferUtils.join
测试        : WebTestClient + StepVerifier
```

---

## 8. 参考资料

- 本项目根目录：`/Users/chenjunbing/Develop/Project/Personal/Spring-Weblux-Learn`
- Webflux 后端入口：`accounting-webflux-backend/src/main/java/com/example/accounting/AccountingWebfluxApplication.java`
- WebMVC 后端入口：`accounting-webmvc-backend/src/main/java/com/example/accounting/AccountingWebmvcApplication.java`
- 共享 DTO/Entity：`accounting-common/src/main/java/com/example/accounting/`
- Spring Webflux 官方文档：<https://docs.spring.io/spring-framework/reference/web/webflux.html>
- Project Reactor 参考：<https://projectreactor.io/docs/core/release/reference/>
