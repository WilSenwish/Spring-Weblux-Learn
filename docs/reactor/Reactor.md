# Reactor Core 横向对比与核心概念

> 基于 reactor-core 3.7.19

本文档对 Reactor 中两个核心抽象 `Flux` 与 `Mono` 进行横向对比，并系统梳理响应式编程的核心概念。详细的方法签名与单方法行为请参考 [Flux.md](./Flux.md) 与 [Mono.md](./Mono.md)。

---

## 一、Reactive Streams 规范概述

Reactive Streams 是一个为响应式流处理定义标准规范的倡议，目标是提供**非阻塞背压**的异步流处理标准。其 API 由 `org.reactivestreams` 包定义，包含四个核心接口。

### 1.1 四个核心接口

#### Publisher（数据生产者）

```java
public interface Publisher<T> {
    void subscribe(Subscriber<? super T> s);
}
```

- **职责**：数据生产者，潜在地提供无限数量的元素。
- **关键约束**：`subscribe` 可被多次调用，每次调用都会创建一个新的 `Subscription`（即冷源语义）。
- **说明**：Reactor 的 `Flux` 和 `Mono` 都实现了该接口。

#### Subscriber（数据消费者）

```java
public interface Subscriber<T> {
    void onSubscribe(Subscription s);
    void onNext(T t);
    void onError(Throwable t);
    void onComplete();
}
```

- **职责**：接收数据与信号。
- **方法说明**：
  - `onSubscribe(Subscription s)`：订阅成功后必定被调用一次，且是**第一个**被调用的方法。Subscriber 通过此处的 `Subscription` 控制拉取。
  - `onNext(T t)`：接收下一个元素。调用次数受 `request(n)` 控制。
  - `onError(Throwable t)`：终止信号，发生错误时调用一次，之后不再有其他信号。
  - `onComplete()`：终止信号，成功完成时调用一次，之后不再有其他信号。
- **互斥性**：`onError` 与 `onComplete` 互斥，二者只能调用其一，且只调用一次。

#### Subscription（订阅桥梁）

```java
public interface Subscription {
    void request(long n);
    void cancel();
}
```

- **职责**：连接 `Publisher` 与 `Subscriber`，是背压控制的入口。
- **方法说明**：
  - `request(long n)`：请求 `n` 个元素。`n <= 0` 会触发 `onError(IllegalArgumentException)`。
  - `cancel()`：取消订阅，停止接收信号。Publisher 应停止调用 Subscriber 的方法，并释放资源。`cancel` 后再调用 `request` 无效。

#### Processor（中间处理器）

```java
public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
}
```

- **职责**：既是一个 `Subscriber`（消费 `T`），又是一个 `Publisher`（生产 `R`），是数据流的中间处理阶段。
- **说明**：Reactor 3.5+ 推荐使用 `Sinks` 替代旧版 `Processor` 实现（详见 [§2.5 Sinks](#25-sinks)）。

### 1.2 响应式流契约（Reactive Streams Contract）

Reactive Streams 规范定义了 17 条规则（§1–§17），核心规则如下：

| 规则编号 | 规则要点 | 说明 |
|---------|---------|------|
| §1 | `Publisher.subscribe` 必须在 `Subscriber` 实例的 `onSubscribe` 之前完成对状态的可见性写入 | 保证订阅前状态可见 |
| §3 | `onSubscribe` 必须只在每个 `Subscriber` 上调用一次 | 订阅是单次事件 |
| §4 | `onSubscribe` 调用后，`onNext`/`onError`/`onComplete` 才能调用 | 订阅是后续信号的前提 |
| §5 | `onComplete`/`onError` 互斥，且终态后不能再调用任何方法 | 终态封闭 |
| §7 | `request(n)` 中 `n <= 0` 时必须用 `onError(IllegalArgumentException)` 终止 | 防止非法请求 |
| **§9** | **`request(n)` 必须支持无界需求（`Long.MAX_VALUE`）** | **背压规则核心**：Subscriber 通过 `request(n)` 控制拉取数量 |
| **§10** | **`request(n)` 与 `cancel()` 必须只影响其自身订阅** | **线程安全规则**：调用必须线程安全 |
| **§11** | **`Subscription` 必须支持在并发环境下被不同线程调用 `request`/`cancel`** | **线程安全规则**：实现需做同步 |
| §13 | `cancel` 后 `request` 通常是 no-op | 取消后停止需求 |
| §15 | **`Publisher` 调用 `Subscriber` 的方法不能阻塞** | **非阻塞规则**：`onNext` 不得阻塞调用线程 |
| §16 | **`Subscriber` 的 `onNext` 必须能处理 `null` 之外的任何 `T`** | **null 规则**：`onNext(null)` 是非法调用，会抛 `NullPointerException` |
| §17 | `Subscriber.onComplete` 之前，`onSubscribe` 必须已调用 | 顺序约束 |

**重点规则解读**：

- **背压规则**：Subscriber 通过 `Subscription.request(n)` 主动声明它能处理的元素数量，Publisher 不得在未收到请求时推送超过请求量的元素。这是响应式流区别于传统观察者模式的核心。
- **非阻塞规则**：`Publisher` 调用 `Subscriber.onNext` 时不能阻塞调用线程。如需阻塞操作，应通过 `Schedulers` 切换线程（详见 [§2.3](#23-调度器schedulers)）。
- **线程安全规则**：`request(n)` 和 `cancel()` 必须线程安全，允许在多线程并发调用。
- **null 规则**：`onNext(null)` 是非法调用。Reactor 中元素不能为 `null`，需要表达"无值"应使用空 Mono/Flux 或 `Optional`。

### 1.3 Reactor 与 Reactive Streams 的关系

Reactor 是 Reactive Streams 规范的官方实现之一（与 RxJava、Akka Streams、Java 9 Flow API 并列）。

- `Flux<T>` 和 `Mono<T>` 都实现了 `Publisher<T>` 接口。
- Reactor 内部使用 `CoreSubscriber`、`CorePublisher` 扩展了原接口，增加了 `Context` 传递、条件式订阅等优化能力。
- Reactor 额外提供了 `Sinks`、`Scheduler`、`Context` 等机制，是 Reactive Streams 之上的丰富扩展。

```
org.reactivestreams.Publisher
        ▲
        │ implements
   ┌────┴────┐
   │         │
 Flux<T>  Mono<T>
```

---

## 二、Reactor 核心概念

### 2.1 Cold vs Hot Publisher

响应式流中的 Publisher 分为两类：**冷源（Cold）** 与 **热源（Hot）**。

#### 对比表

| 维度 | Cold Publisher（冷源） | Hot Publisher（热源） |
|------|---------------------|---------------------|
| 数据生产时机 | 每个订阅者订阅时才开始生产 | 无论有无订阅者都开始生产 |
| 订阅者看到的数据 | 完整数据流，每个订阅者独立 | 共享同一数据流，后加入者只能收到后续数据 |
| 订阅触发副作用 | 每次订阅都触发（如 HTTP 请求会重复执行） | 只触发一次（共享上游） |
| 典型代表 | `Flux.just`、`Flux.fromIterable`、`Flux.range`、`Mono.fromCallable` | `Sinks.many()`、`Flux.share()`、`Flux.cache()`、`Flux.replay()` |
| 缓存 | 不缓存，每次重新生产 | 通常缓存或共享 |

#### 代码示例：Cold Publisher

```java
Flux<Integer> cold = Flux.range(1, 3)
        .doOnSubscribe(s -> System.out.println("subscribed!"));

// 每个订阅者都会触发 "subscribed!"，且都收到完整的 1,2,3
cold.subscribe(v -> System.out.println("A: " + v));  // A: 1, A: 2, A: 3
cold.subscribe(v -> System.out.println("B: " + v));  // 再次 subscribed!, B: 1, B: 2, B: 3
```

#### 代码示例：Hot Publisher

```java
// share() 将冷源转为热源：多个订阅者共享一次上游订阅
Flux<Long> hot = Flux.interval(Duration.ofMillis(100)).share();

hot.subscribe(v -> System.out.println("A: " + v));
Thread.sleep(250);
// B 后加入，只能收到 A 之后的数据，且不再触发新的上游订阅
hot.subscribe(v -> System.out.println("B: " + v));
// 输出形如：
// A: 0
// A: 1
// A: 2
// B: 2  (B 与 A 共享)
// A: 3
// B: 3
```

#### 产生 Hot Publisher 的操作符

| 操作符 | 适用类型 | 行为 |
|--------|---------|------|
| `share()` | Flux / Mono | `publish().refCount()`，首个订阅触发，全部取消则取消上游 |
| `cache()` / `cache(ttl)` | Flux / Mono | 缓存上游信号，后续订阅者直接走缓存 |
| `replay(n)` / `replay(ttl)` | Flux | `ConnectableFlux`，缓存指定历史元素供重放 |
| `publish()` | Flux / Mono | 转 `ConnectableFlux`，需 `connect()` 或 `autoConnect(n)` |
| `Sinks.many().multicast()` | Flux | 显式多播热源，由开发者手动推送数据 |
| `Sinks.many().replay()` | Flux | 缓存历史并重放的热源 |
| `Flux.push` / `Flux.create` + 共享 | Flux | 当 `FluxSink` 被多订阅者共享时表现为热源 |

> **说明**：`Mono` 因只有 0/1 元素，"冷热"区别主要体现在是否共享上游订阅。`Mono.share()` 等价于 `Flux.shareNext()`。

---

### 2.2 背压（Backpressure）

#### 背压的概念

背压是指**消费者通过 `Subscription.request(n)` 告诉生产者它能处理多少数据**，从而避免生产者过快推送导致消费者被压垮（OOM、队列膨胀）的机制。

- 传统推模式：生产者以自己的速度推送，消费者被动接收 → 易压垮消费者。
- 响应式拉模式：消费者按需 `request(n)`，生产者只推送请求量 → 消费者掌控节奏。

#### `Subscription.request(n)` 的工作机制

```java
Flux.range(1, Integer.MAX_VALUE)
    .subscribe(new BaseSubscriber<Integer>() {
        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            // 订阅时只请求 5 个
            request(5);
        }

        @Override
        protected void hookOnNext(Integer value) {
            System.out.println("Received: " + value);
            // 每消费 1 个再请求 1 个，控制节奏
            request(1);
        }
    });
```

- `request(n)` 累加到需求量中，Publisher 在不超过累计需求量的前提下推送。
- `request(Long.MAX_VALUE)` 表示无界需求（等价于推模式）。
- Reactor 大多数操作符采用"预取 + 补充 75%"策略（默认预取 32 或 256），自动管理下游请求。

#### Flux 中的背压策略操作符

当上游推得比下游快（下游需求不足）时，可用以下操作符定义溢出策略：

| 操作符 | 行为 |
|--------|------|
| `onBackpressureBuffer()` | 缓冲所有溢出元素（无界或限界） |
| `onBackpressureBuffer(maxSize)` | 限界缓冲，超限按 `BufferOverflowStrategy` 处理 |
| `onBackpressureDrop(Consumer<T>)` | 丢弃溢出元素，可选通知丢弃回调 |
| `onBackpressureLatest()` | 只保留最新一个元素，丢弃之前的 |
| `onBackpressureError()` | 溢出时直接抛 `OverflowException` 终止 |

#### `Flux.create()` 中的 `OverflowStrategy`

`Flux.create(emitter, OverflowStrategy)` 接受枚举指定背压策略：

| 策略 | 说明 |
|------|------|
| `BUFFER`（默认） | 缓冲所有未消费元素，无界缓冲可能 OOM |
| `DROP` | 下游需求不足时丢弃新元素 |
| `LATEST` | 只保留最新一个元素 |
| `ERROR` | 立即抛 `OverflowException` |
| `IGNORE` | 完全忽略背压，下游被压垮自负责任 |

```java
Flux.create(sink -> {
    for (int i = 0; i < 1000; i++) {
        sink.next(i);  // 下游消费慢时按策略处理
    }
    sink.complete();
}, FluxSink.OverflowStrategy.DROP)
    .onBackpressureDrop(v -> System.out.println("Dropped: " + v))
    .subscribe(v -> System.out.println("Got: " + v));
```

#### Mono 为什么通常不需要背压

`Mono` 只发射 0 或 1 个元素：

- 单元素场景下不存在"生产快于消费"的问题，消费者 `request(1)` 即可获取全部数据。
- `Mono` 的操作符通常在 `onSubscribe` 时直接 `request(1)` 或无界请求。
- 因此 `Mono` 没有 `onBackpressureXxx` 系列操作符。
- 唯一相关的语义是 `Mono` 的 `request(0)` 是合法的 no-op（用于"我不想接收数据，但希望订阅"）。

> **结论**：背压是 Flux（多元素流）的核心关切点，Mono 因 0/1 语义而天然无需背压控制。

---

### 2.3 调度器（Schedulers）

Reactor 通过 `Scheduler` 抽象线程调度，提供以下内置调度器：

| 调度器 | 说明 | 适用场景 |
|--------|------|---------|
| `Schedulers.immediate()` | 当前线程直接执行 | 不切换线程（默认行为） |
| `Schedulers.single()` | 单线程复用（所有任务串行） | 串行化任务、顺序敏感 |
| `Schedulers.boundedElastic()` | 有界弹性线程池（默认 10×CPU 核数，上限 100k 任务排队） | **阻塞 I/O**：JDBC、阻塞 HTTP、文件 IO |
| `Schedulers.parallel()` | 固定大小线程池（=CPU 核数） | **CPU 密集型**：计算、转换 |
| `Schedulers.fromExecutor(Executor)` | 自定义 `Executor` 适配 | 复用已有线程池 |
| `Schedulers.fromExecutorService(ExecutorService)` | 自定义 `ExecutorService` 适配 | 复用已有线程池（推荐） |

> **关键区别**：`boundedElastic` 专为阻塞操作设计（每个任务可长时间占用线程）；`parallel` 不适合阻塞操作（阻塞会拖慢 CPU 任务）。

#### `publishOn` vs `subscribeOn`

| 操作符 | 影响范围 | 位置规则 | 多次调用 |
|--------|---------|---------|---------|
| `publishOn(scheduler)` | 影响其**下游**操作符的执行线程 | 后续 onNext/onComplete/onError 在该 Scheduler 执行 | 多次调用以最后一个为准（链式覆盖） |
| `subscribeOn(scheduler)` | 影响整个链路**最上游**的订阅线程 | 源（及上游操作符）在该 Scheduler 被订阅 | 多次调用只第一次生效（最靠近源） |

#### 代码示例

```java
Flux.range(1, 3)
    // subscribeOn 影响源订阅线程：让 range 在 boundedElastic 上订阅
    .subscribeOn(Schedulers.boundedElastic())
    .map(i -> {
        System.out.println("map1: " + i + " on " + Thread.currentThread().getName());
        return i * 10;
    })
    // publishOn 切换下游线程：map2 及之后在 parallel 上执行
    .publishOn(Schedulers.parallel())
    .map(i -> {
        System.out.println("map2: " + i + " on " + Thread.currentThread().getName());
        return i + 1;
    })
    .subscribe(v -> System.out.println("subscribe: " + v + " on " + Thread.currentThread().getName()));

// 输出（线程名示意）：
// map1: 1 on boundedElastic-1
// map1: 2 on boundedElastic-1
// map1: 3 on boundedElastic-1
// map2: 10 on parallel-1
// subscribe: 11 on parallel-1
// map2: 20 on parallel-1
// subscribe: 21 on parallel-1
// ...
```

> **记忆口诀**：`subscribeOn` 管"订阅从哪开始"，`publishOn` 管"接下来在哪跑"。

---

### 2.4 Context（上下文）

#### 概念

Reactor `Context` 是一个**沿响应式链从下游向上游传播**的键值对存储，用于在操作符之间传递不依赖于方法参数的上下文信息（如 traceId、用户身份、租户 ID）。

- **方向**：从下游（订阅点）向上游（源）传播，与数据流方向相反。
- **不可变**：`Context` 是不可变的，每次 `contextWrite` 产生新的 `Context`。
- **生命周期**：与 `Subscription` 绑定，每个订阅有独立的 `Context`。

#### 核心操作符

| 操作符 | 方向 | 作用 |
|--------|------|------|
| `contextWrite(ContextView)` | 写入（下游→上游） | 向 Context 追加键值对 |
| `contextWrite(Function<Context, Context>)` | 写入（下游→上游） | 基于当前 Context 计算新 Context |
| `deferContextual(Function<ContextView, Publisher>)` | 读取（上游消费） | 订阅时读取上游传播上来的 ContextView 构造 Publisher |
| `transformDeferredContextual(BiFunction)` | 读取（上游消费） | 订阅时基于 ContextView 延迟变换 |
| `contextCapture()` | 捕获 | 显式捕获当前线程的 ThreadLocal 到 Context（需 context-propagation 库） |

#### 与 ThreadLocal 的对比

| 维度 | ThreadLocal | Reactor Context |
|------|-------------|-----------------|
| 传播方向 | 线程内可见 | 沿响应式链向上游传播 |
| 跨线程 | 默认不传播（需 `InheritableThreadLocal` 或显式传递） | 自动随 Subscription 传播 |
| 与响应式兼容 | **不兼容**（线程切换会丢失） | **兼容**（与订阅绑定，不依赖线程） |
| 不可变性 | 可变 | 不可变 |
| 使用场景 | 命令式代码、框架内部 | 响应式链路上下文传递（traceId、用户信息） |

#### 代码示例

```java
String result = Mono.deferContextual(ctx -> {
            // 读取上游传播上来的 Context
            String user = ctx.getOrDefault("user", "anonymous");
            String traceId = ctx.getOrDefault("traceId", "none");
            return Mono.just("user=" + user + ", traceId=" + traceId);
        })
        // contextWrite 在 deferContextual 的下游，但 Context 是向上传播的
        .contextWrite(Context.of("user", "alice", "traceId", "abc-123"))
        .block();

System.out.println(result);  // user=alice, traceId=abc-123
```

> **关键**：`contextWrite` 必须放在读取上下文的操作符的**下游**（链中靠后），因为 Context 是从订阅点向上游传播的。

---

### 2.5 Sinks

#### 概念

`Sinks` 是 Reactor 3.5+ 推荐的**命令式数据推送方式**，用于在响应式链之外安全地向流中推送数据。它替代了旧版的 `Processor` 系列（`DirectProcessor`、`EmitterProcessor`、`ReplayProcessor` 等已废弃）。

- **优势**：线程安全、API 清晰、明确区分单播/多播/重放语义。
- **核心方法**：`tryEmitNext(T)` / `emitNext(T, EmitFailureHandler)`（推送元素）、`tryEmitError(Throwable)` / `tryEmitComplete()`（终止）。

#### Sinks 类型

| 工厂方法 | 返回类型 | 语义 | 订阅者数量 | 缓存 |
|---------|---------|------|----------|------|
| `Sinks.one()` | `Sinks.One<T>` | 单值热源（类似 `Mono`） | 多 | 缓存唯一值 |
| `Sinks.empty()` | `Sinks.Empty<T>` | 单值热源（仅完成/错误） | 多 | 无值 |
| `Sinks.many().unicast()` | `Sinks.Many<T>` | 单播热源 | **仅 1 个** | 默认无（可配缓冲） |
| `Sinks.many().multicast()` | `Sinks.Many<T>` | 多播热源（onBackpressureBuffer 语义） | 多 | 无 |
| `Sinks.many().multicast().onBackpressureBuffer()` | `Sinks.Many<T>` | 多播 + 缓冲 | 多 | 缓冲所有元素 |
| `Sinks.many().replay()` | `Sinks.Many<T>` | 多播 + 重放历史 | 多 | 缓存所有/指定数量 |
| `Sinks.many().replay().limit(n)` | `Sinks.Many<T>` | 多播 + 重放最后 N 个 | 多 | 缓存 N 个 |
| `Sinks.many().replay().all()` | `Sinks.Many<T>` | 多播 + 重放所有 | 多 | 缓存所有 |
| `Sinks.unsafe().many()` | `Sinks.Many<T>` | 非线程安全版本（性能更高） | — | — |

#### 代码示例

```java
// 创建一个多播热源
Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

// 订阅者 A
sink.asFlux().subscribe(v -> System.out.println("A: " + v));

// 推送数据（命令式）
sink.tryEmitNext("hello");
sink.tryEmitNext("world");

// 订阅者 B 后加入，只能收到之后的数据
sink.asFlux().subscribe(v -> System.out.println("B: " + v));

sink.tryEmitNext("again");
sink.tryEmitComplete();

// 输出：
// A: hello
// A: world
// A: again
// B: again
```

```java
// Sinks.one() 示例：单值热源
Sinks.One<String> one = Sinks.one();
one.tryEmitValue("singleton");

// 多个订阅者都收到同一个值
one.asMono().subscribe(v -> System.out.println("sub1: " + v));
one.asMono().subscribe(v -> System.out.println("sub2: " + v));
```

---

### 2.6 操作符链执行模型

#### 装配时（Assembly Time） vs 订阅时（Subscription Time）

Reactor 操作符链的执行分为两个阶段：

| 阶段 | 时机 | 行为 |
|------|------|------|
| **装配时** | 链式调用编写时 | 只构建处理图（描述数据如何处理），不执行任何数据处理逻辑。每个操作符返回新的 `Flux`/`Mono`，原始流不变。 |
| **订阅时** | `subscribe()` 被调用时 | 真正开始数据流动：从订阅点向上游传播订阅信号，到源后源开始生产数据，沿链向下传播 onNext/onError/onComplete。 |

#### "nothing happens until you subscribe" 原则

这是响应式编程的**核心原则**：在调用 `subscribe()` 之前，链上的任何操作符都不会执行，数据流也不会产生。这是与命令式编程最大的区别。

#### 代码示例

```java
// 装配阶段：以下代码不产生任何输出，只是构建了处理图
Flux<Integer> flux = Flux.range(1, 5)
        .map(i -> {
            System.out.println("mapping: " + i);  // 此时不会执行
            return i * 2;
        })
        .filter(i -> i > 4)
        .doOnNext(i -> System.out.println("filtered: " + i));

System.out.println("链已装配，但未订阅，无输出");

// 订阅阶段：此时才真正执行
flux.subscribe(v -> System.out.println("final: " + v));

// 输出：
// 链已装配，但未订阅，无输出
// mapping: 1
// mapping: 2
// mapping: 3
// filtered: 6
// final: 6
// mapping: 4
// mapping: 5
// filtered: 10
// final: 10
```

#### 延迟装配

某些操作符会**显式延迟装配**到订阅时：

| 操作符 | 行为 |
|--------|------|
| `defer(Supplier)` | 每次订阅时调用 Supplier 创建新 Publisher |
| `deferContextual(Function)` | 每次订阅时基于 ContextView 创建新 Publisher |
| `transformDeferred(Function)` | 每次订阅时重新执行变换函数 |
| `transformDeferredContextual(BiFunction)` | 每次订阅时基于 ContextView 重新变换 |
| `using(Callable, Function, Consumer)` | 每次订阅时获取/清理资源 |

```java
// defer 示例：每次订阅都重新获取当前时间
Mono<Long> now = Mono.defer(() -> Mono.just(System.currentTimeMillis()));

now.subscribe(t -> System.out.println("sub1: " + t));
Thread.sleep(1000);
now.subscribe(t -> System.out.println("sub2: " + t));  // 时间不同
```

---

## 三、Flux ↔ Mono 方法映射表

### 3.1 共有方法（Flux 和 Mono 都有的操作符）

> 详细方法签名见 [Flux.md](./Flux.md) 与 [Mono.md](./Mono.md)。

| 方法名 | Flux 行为 | Mono 行为 | 语义差异说明 |
|--------|----------|----------|-------------|
| `map` | 对每个元素同步转换（1:1） | 对唯一元素同步转换 | Flux 多次触发，Mono 至多一次 |
| `mapNotNull` | 转换返回 null 时跳过该元素 | 转换返回 null 时完成空 Mono | Flux 过滤 null，Mono 等价 map+过滤 |
| `filter` | 保留满足谓词的元素 | 满足则发射，否则空 Mono | Mono 不满足即空完成 |
| `filterWhen` | 异步过滤每个元素 | 异步测试唯一元素 | Mono 至多一次判断 |
| `flatMap` | 每元素展开为 Publisher，并发订阅，结果交错 | 0/1 元素转为另一 Mono，无并发 | **语义差异大**，详见 [§4.1](#41-flatmap) |
| `flatMapIterable` | 每元素展开为 Iterable 逐个发射 | 唯一元素展开为 Iterable，返回 Flux | Mono 返回 Flux |
| `cast` | 类型强转 | 类型强转 | 行为一致 |
| `ofType` | 类型过滤 + 转换 | 类型过滤 + 转换 | Mono 至多一次 |
| `defaultIfEmpty` | 空 Flux 时发默认值 | 空 Mono 时发默认值 | 详见 [§4.4](#44-defaultifempty) |
| `switchIfEmpty` | 空时切换备选 Publisher | 空时切换备选 Mono | 行为一致，Flux 备选可多元素 |
| `onErrorReturn` | 错误时发回退值后完成 | 错误时发回退值后完成 | 行为一致 |
| `onErrorResume` | 错误时切换到 fallback Publisher | 错误时切换到 fallback Mono | Flux fallback 可多元素 |
| `onErrorMap` | 转换错误类型 | 转换错误类型 | 行为一致 |
| `onErrorComplete` | 错误时转 onComplete | 错误时转 onComplete | 行为一致 |
| `onErrorContinue` | 上游兼容操作符丢弃出错元素继续 | 主要为将策略传播给上游 Flux | Mono 本身意义不大 |
| `onErrorStop` | 恢复"错误即终止"语义 | 同左 | 用于隔离 OEC 范围 |
| `retry` | 错误时重订阅 | 错误时重订阅 | 详见 [§4.5](#45-retryretrywhen) |
| `retryWhen` | 基于 Retry 策略重试 | 基于 Retry 策略重试 | 行为一致 |
| `doOnNext` | 每元素发射前副作用 | 唯一元素发射前副作用 | 详见 [§4.3](#43-doonnext) |
| `doOnEach` | 每信号（Signal）副作用 | 每信号副作用 | 行为一致 |
| `doOnSubscribe` | 订阅时副作用 | 订阅时副作用 | 行为一致 |
| `doOnRequest` | 下游 request 时副作用 | 下游 request 时副作用 | 行为一致 |
| `doOnCancel` | 被取消时副作用 | 被取消时副作用 | 行为一致 |
| `doOnError` | 错误时副作用 | 错误时副作用 | 行为一致 |
| `doOnTerminate` | 终止前副作用（含错误） | 终止前副作用 | Mono 中 onNext 即终止 |
| `doAfterTerminate` | 终止后副作用 | 终止后副作用 | 行为一致 |
| `doFirst` | 订阅链最开头副作用 | 同左 | 多次调用反向执行 |
| `doFinally` | 任意终止（含 cancel）副作用 | 同左 | 推荐用于资源清理 |
| `doOnDiscard` | 内部丢弃元素时清理 | 同左 | 需操作符支持 Discard Support |
| `tap` | 全生命周期 SignalListener | 同左 | 新代副作用入口 |
| `elapsed` | 元素配对距上次间隔(ms) | 元素配对距订阅间隔(ms) | Mono 只有一个元素 |
| `timestamp` | 元素配对时间戳 | 元素配对时间戳 | 行为一致 |
| `timed` | 包装为 Timed（含 3 重时间信息） | 包装为 Timed | 行为一致 |
| `delaySubscription` | 延迟订阅上游 | 延迟订阅上游 | 行为一致 |
| `cache` | 缓存所有/指定元素 | 缓存唯一元素或空信号 | 详见 [§4.6](#46-cache) |
| `timeout` | 超时控制 | 超时控制 | Mono 只关注首个元素超时 |
| `log` | 记录所有信号 | 记录所有信号 | 行为一致 |
| `checkpoint` | 装配点标记 | 装配点标记 | 行为一致 |
| `metrics` | 注册指标（已废弃，推荐 tap） | 注册指标（已废弃） | 行为一致 |
| `name` | 链路命名 | 链路命名 | 行为一致 |
| `tag` | 链路键值标签 | 链路键值标签 | 行为一致 |
| `publishOn` | 切换下游执行线程 | 切换下游执行线程 | 行为一致 |
| `subscribeOn` | 切换上游订阅线程 | 切换上游订阅线程 | 行为一致 |
| `hide` | 隐藏实现类型 | 隐藏实现类型 | 行为一致 |
| `repeat` | 完成后重订阅，返回 Flux | 完成后重订阅，返回 Flux | 都返回 Flux |
| `repeatWhen` | 基于 companion 流重复 | 基于 companion 流重复 | 都返回 Flux |
| `onTerminateDetach` | 终止时解引用 | 终止时解引用 | 行为一致 |
| `as` | 装配时变换为任意类型 | 装配时变换为任意类型 | 行为一致 |
| `transform` | 装配时变换 | 装配时变换 | 行为一致 |
| `transformDeferred` | 订阅时变换 | 订阅时变换 | 行为一致 |
| `transformDeferredContextual` | 订阅时基于 Context 变换 | 订阅时基于 Context 变换 | 行为一致 |
| `contextWrite` | 写入 Context | 写入 Context | 行为一致 |
| `contextCapture` | 捕获 ThreadLocal 到 Context | 捕获 ThreadLocal 到 Context | 行为一致 |
| `deferContextual` | 延迟基于 Context 创建 | 延迟基于 Context 创建 | 行为一致 |
| `subscribe` | 触发订阅 | 触发订阅 | 行为一致 |
| `subscribeWith` | 用指定 Subscriber 订阅 | 用指定 Subscriber 订阅 | 行为一致 |
| `block` | — | 阻塞获取唯一值 | Mono 独有（Flux 用 blockFirst/blockLast） |
| `cancelOn` | 调度取消信号 | 调度取消信号 | 行为一致 |
| `expand` | BFS 递归展开，返回 Flux | BFS 递归展开，返回 Flux | 都返回 Flux |
| `expandDeep` | DFS 递归展开，返回 Flux | DFS 递归展开，返回 Flux | 都返回 Flux |
| `handle` | 灵活 0/1/N 输出 | 灵活 0/1 输出 | Mono 至多一次 next |
| `materialize` | 信号转 Signal 元素 | 信号转 Signal 元素 | Mono 产出至多一个 Signal |
| `dematerialize` | Signal 还原为信号 | Signal 还原为信号 | 行为一致 |
| `concatWith` | 串联另一 Publisher，返回 Flux | 串联另一 Publisher，返回 Flux | 都返回 Flux |
| `mergeWith` | 并发合并，返回 Flux | 并发合并，返回 Flux | 都返回 Flux |
| `zipWith` | 与另一源配对，返回 Flux | 与另一 Mono 配对，返回 Mono | Mono 返回 Mono |
| `startWith` | 前置值/Publisher | 前置值/Publisher，返回 Flux | Mono 返回 Flux |
| `or` | 与另一源竞速，返回 Flux | 与另一 Mono 竞速，返回 Mono | Mono 返回 Mono |
| `just`（静态） | 可接受多个元素 | 只接受一个元素 | 详见 [§4.2](#42-just) |
| `empty`（静态） | 创建空 Flux | 创建空 Mono | 行为一致 |
| `error`（静态） | 创建错误 Flux | 创建错误 Mono | 行为一致 |
| `never`（静态） | 永不结束的 Flux | 永不结束的 Mono | 行为一致 |
| `from`（静态） | 从 Publisher 转 Flux | 从 Publisher 转 Mono（取首元素后取消） | Mono 会取消源 |
| `using`（静态） | 资源管理 Flux | 资源管理 Mono | 行为一致 |
| `usingWhen`（静态） | 异步资源管理 Flux | 异步资源管理 Mono | 行为一致 |
| `create`（静态） | 编程式创建 Flux（多线程） | 编程式创建 Mono（单值） | Flux 用 FluxSink，Mono 用 MonoSink |
| `defer`（静态） | 延迟创建 Flux | 延迟创建 Mono | 行为一致 |
| `range`（静态） | 发射递增整数序列 | **Mono 无此方法** | 仅 Flux |
| `filterWhen` | 异步过滤 | 异步过滤 | 行为一致 |
| `take(Duration)` / `takeUntilOther` | 时间窗口取元素 | 超时则完成（非报错） | Mono 至多一次 |

> **说明**：`fromArray` 仅 Flux 有（Mono 0/1 语义不需要）；`range` 仅 Flux 有。

---

### 3.2 Flux 独有方法

> 这些方法在 `Flux` 中存在，`Mono` 因 0/1 语义而不需要或无意义。

| 方法名 | 功能 | 为什么 Mono 不需要 |
|--------|------|-------------------|
| `buffer` / `bufferTimeout` / `bufferUntil` / `bufferWhile` / `bufferWhen` / `bufferUntilChanged` | 将元素分批收集到集合 | Mono 至多 1 个元素，无需分批 |
| `window` / `windowTimeout` / `windowUntil` / `windowWhile` / `windowWhen` / `windowUntilChanged` | 切分为嵌套 Flux 窗口 | Mono 至多 1 个元素，无需切窗 |
| `groupBy` | 按 key 分组为 GroupedFlux | Mono 至多 1 个元素，无需分组 |
| `flatMapSequential` / `flatMapSequentialDelayError` | 并发展开但保序输出 | Mono 只有 0/1 元素，无并发保序问题 |
| `flatMapDelayError` | 并发展开，延迟错误 | Mono 无多元素并发 |
| `concatMap` / `concatMapDelayError` | 顺序展开内层 Publisher | Mono 单元素场景 flatMap 已等价 |
| `concatMapIterable` | 顺序展开 Iterable | Mono 用 flatMapIterable |
| `switchMap` | 切换式展开（新元素取消旧内层流） | Mono 单元素无需切换 |
| `switchOnFirst` | 基于首信号切换 | Mono 单元素无需切换 |
| `scan` / `scanWith` | 累加发射中间值 | Mono 单值无中间值序列 |
| `take` / `takeLast` / `takeUntil` / `takeWhile` / `takeUntilOther` | 取前 N / 最后 N / 直到 / 当 | Mono 至多 1 元素，取首即 next() |
| `skip` / `skipLast` / `skipUntil` / `skipUntilOther` / `skipWhile` | 跳过前 N / 最后 N / 直到 / 当 | Mono 至多 1 元素，跳过即空 |
| `limitRate` / `limitRequest` | 限制请求速率/总量 | Mono 单值无速率问题 |
| `elementAt` | 取第 N 个元素 | Mono 单值即首元素 |
| `last` | 取最后一个元素 | Mono 单值即首元素 |
| `ignoreElements` | 忽略元素只留完成信号 | Mono 用 ignoreElement() |
| `next` | 取第一个元素转 Mono | Mono 本身即首元素 |
| `single` / `singleOrEmpty` | 断言单元素 | Mono 本就是单元素语义 |
| `distinct` / `distinctUntilChanged` | 去重 | Mono 单值无需去重 |
| `sample` / `sampleFirst` / `sampleTimeout` | 采样/去抖 | Mono 单值无需采样 |
| `collect` / `collectList` / `collectSortedList` / `collectMap` / `collectMultimap` | 收集为容器 | Mono 单值无需收集 |
| `reduce` / `reduceWith` | 聚合为单值 | Mono 本身即单值 |
| `count` | 计数 | Mono 用 hasElement 判断有无 |
| `all` / `any` | 全部/任一满足 | Mono 单值用 map/filter |
| `hasElement(T)` | 是否包含指定值 | Mono 用 hasElement() 返回 Boolean |
| `hasElements` | 是否有任意元素 | Mono 用 hasElement() |
| `onBackpressureBuffer` / `onBackpressureDrop` / `onBackpressureError` / `onBackpressureLatest` | 背压策略 | Mono 单值无背压问题 |
| `index` | 为元素附加索引 | Mono 单值无需索引 |
| `mergeOrderedWith` / `mergeComparingWith` | 有序/比较器合并 | Mono 单值无需合并 |
| `withLatestFrom` | 与另一源最新值组合 | Mono 单值无需 |
| `join` / `groupJoin` | 时间窗口 join | Mono 单值无需 |
| `zipWithIterable` | 与 Iterable 配对 | Mono 单值无需 |
| `combineLatestWith`（无实例方法，用静态 `combineLatest`） | 最新值组合 | Mono 单值无需 |
| `blockFirst` / `blockLast` | 阻塞取首/末元素 | Mono 用 block() |
| `toIterable` / `toStream` | 转阻塞 Iterable/Stream | Mono 用 block/blockOptional |
| `toFuture`（Flux 无此方法） | — | 实际是 Mono 独有 |
| `sort` | 缓存后排序 | Mono 单值无需排序 |
| `parallel` | 并行分发到 ParallelFlux | Mono 单值无需并行 |
| `publish` | 转 ConnectableFlux | Mono 有 publish(Function) 多播 |
| `share` | 共享热源 | Mono 也有 share() |
| `shareNext` | 共享首元素转 Mono | Mono 单值即自身 |
| `replay` | 缓存重放 | Mono 用 cache() |
| `publishNext` | — | 实际 Flux 无此方法 |
| `concat` / `concatDelayError`（静态） | 串联多源 | Mono 用 when/and |
| `merge` / `mergeDelayError` / `mergeSequential` / `mergeSequentialDelayError` / `mergeOrdered` / `mergePriority` / `mergePriorityDelayError` / `mergeComparing` / `mergeComparingDelayError`（静态） | 多种合并语义 | Mono 用 mergeWith/zip/when |
| `combineLatest`（静态） | 最新值组合 | Mono 用 zip |
| `switchOnNext`（静态） | 切换式合并 | Mono 单值无需 |
| `first` / `firstWithSignal` / `firstWithValue`（静态） | 选取最快源 | Mono 也有对应版本 |
| `interval`（静态） | 周期发射递增 Long | Mono 用 delay(Duration) |
| `push` / `generate`（静态） | 编程式创建（单线程/同步生成） | Mono 用 create |
| `fromIterable` / `fromStream`（静态） | 从 Iterable/Stream 创建 | Mono 用 fromIterable 转单值无意义 |
| `range`（静态） | 发射递增整数 | Mono 单值无序列 |
| `concatWithValues` | 串联指定值 | Mono 用 thenReturn |
| `delayElements` / `delaySequence` | 逐元素/整体延迟 | Mono 用 delayElement |

---

### 3.3 Mono 独有方法

> 这些方法在 `Mono` 中存在，`Flux` 因多元素语义而不需要或无意义。

| 方法名 | 功能 | 为什么 Flux 没有 |
|--------|------|----------------|
| `flatMapMany` | 将唯一元素展开为多元素 Flux | Flux 用 flatMap 即可（已返回 Flux） |
| `then()` | 忽略元素，仅保留完成信号，返回 Mono\<Void\> | Flux 用 then()（实际 Flux 也有，但 Mono 语义更强） |
| `then(Mono<V> other)` | 完成后切换到另一 Mono | Flux 用 thenMany |
| `thenEmpty(Publisher<Void>)` | 完成后等待另一 Publisher\<Void\> | Flux 也有 thenEmpty，但 Mono 更常用 |
| `thenMany(Publisher<V>)` | 完成后切换到 Flux | Flux 也有 thenMany |
| `thenReturn(V)` | 完成后发射固定值 | Flux 多元素语义下"完成后发值"无意义 |
| `and(Publisher)` | 合并终止信号，返回 Mono\<Void\> | Flux 多元素无单一"终止"概念 |
| `when` / `whenDelayError`（静态） | 聚合多 Publisher 完成信号 | Flux 多元素无单一完成 |
| `doOnSuccess(Consumer<T>)` | 成功完成时回调（T 可 null） | Flux 无"单值成功"概念 |
| `blockOptional()` | 阻塞返回 Optional\<T\> | Flux 多元素无法用 Optional 表达 |
| `toFuture()` | 转 CompletableFuture\<T\> | Flux 多元素无法用单值 Future 表达（用 collectList().toFuture()） |
| `fromCallable`（静态） | 从 Callable 创建 | Flux 用 fromCallable 转 Flux 无意义（单值） |
| `fromSupplier`（静态） | 从 Supplier 创建 | Flux 无单值语义 |
| `fromRunnable`（静态） | 从 Runnable 创建 | Flux 无单值语义 |
| `fromFuture` / `fromCompletionStage`（静态） | 从 CompletableFuture/CompletionStage 创建 | Flux 单值语义不匹配 |
| `fromDirect`（静态） | 从 Publisher 转 Mono 不做基数检查 | Flux 无需转 Mono |
| `justOrEmpty`（静态） | 从可空值/Optional 创建 | Flux 元素不可为 null |
| `delayElement` | 延迟唯一元素 | Flux 用 delayElements |
| `delayUntil` | 延迟元素直到触发 Publisher 完成 | Flux 也有，但 Mono 更常用 |
| `zipWhen` | 用自身值生成第二个 Mono 再组合 | Flux 用 zipWhen 需多元素语义 |
| `repeatWhenEmpty` | 空完成时重订阅，返回 Mono | Flux 无单值"空"概念 |
| `single()` / `singleOptional()` | 断言单元素 / 包装 Optional | Flux 的 single() 返回 Mono |
| `ignoreElement()` | 忽略元素只留完成（Mono 版） | Flux 用 ignoreElements() |
| `hasElement()` | 是否有元素，返回 Mono\<Boolean\> | Flux 用 hasElements() |
| `sequenceEqual`（静态） | 比较两个 Publisher 是否相同 | Mono 单值比较更有意义 |
| `delay(Duration)`（静态） | 延迟后发射 0L | Flux 用 interval |
| `cacheInvalidateIf` / `cacheInvalidateWhen` | 值导向缓存失效 | Flux 多值缓存失效语义复杂 |

---

## 四、同名操作符语义差异分析

### 4.1 flatMap

`flatMap` 是 Flux 与 Mono 中**语义差异最大**的同名操作符。

#### 对比

| 维度 | Flux.flatMap | Mono.flatMap |
|------|-------------|-------------|
| 输入元素数量 | 0..N | 0..1 |
| 转换函数返回 | `Publisher<R>` | `Mono<R>` |
| 输出类型 | `Flux<R>` | `Mono<R>` |
| 并发性 | 并发订阅多个内层流（默认 256） | 无并发（至多 1 个元素） |
| 结果顺序 | 按到达交错（不保序） | N/A（单元素） |
| 错误处理 | 任一内层流出错立即终止 | 内层流出错即终止 |

#### 代码示例对比

```java
// Flux.flatMap：每元素展开为 Publisher，并发订阅，结果交错
Flux.just(1, 2, 3)
    .flatMap(i -> Flux.just(i, i * 10))  // 每个元素展开为 2 个
    .subscribe(v -> System.out.print(v + " "));
// 可能输出: 1 10 2 20 3 30 或 1 2 10 3 20 30（顺序不保证）

// Mono.flatMap：唯一元素转换为另一个 Mono
Mono.just(5)
    .flatMap(i -> Mono.just(i * 10))  // 转换为另一个 Mono
    .subscribe(v -> System.out.println(v));  // 50

// Mono.flatMap 不会并发，因为只有一个元素
Mono.just("user-123")
    .flatMap(id -> findUserById(id))  // 返回 Mono<User>
    .subscribe(user -> System.out.println(user));
```

> **关键**：Mono.flatMap 等价于"异步 map"，将一个 Mono 转换为另一个 Mono；Flux.flatMap 是真正的并发展开。

---

### 4.2 just

| 维度 | Flux.just | Mono.just |
|------|----------|----------|
| 方法签名 | `just(T data)` / `just(T... data)` | `just(T data)` |
| 接受元素数量 | 1 个或多个 | 仅 1 个 |
| 是否支持可变参数 | 支持（`just("a", "b", "c")`） | 不支持 |
| null 元素 | 不允许 | 不允许 |

```java
Flux<String> flux = Flux.just("A", "B", "C");  // 发射 3 个元素
Mono<String> mono = Mono.just("only one");      // 发射 1 个元素
// Mono.just("A", "B")  // 编译错误：Mono.just 只接受单参数
```

---

### 4.3 doOnNext

| 维度 | Flux.doOnNext | Mono.doOnNext |
|------|-------------|-------------|
| 触发次数 | 每个元素发射前触发（0..N 次） | 唯一元素发射前触发（0 或 1 次） |
| 与 doOnSuccess 关系 | 无 doOnSuccess | doOnNext 只在有值时触发；doOnSuccess 在空完成时也触发（传 null） |
| 触发时机 | onNext 信号传播**前** | onNext 信号传播**前** |

```java
// Flux.doOnNext：每个元素都触发
Flux.just(1, 2, 3).doOnNext(v -> System.out.println("emit: " + v)).subscribe();
// 输出: emit: 1, emit: 2, emit: 3

// Mono.doOnNext：只有有值时触发
Mono.just(1).doOnNext(v -> System.out.println("emit: " + v)).subscribe();  // emit: 1
Mono.empty().doOnNext(v -> System.out.println("emit: " + v)).subscribe();  // 无输出

// Mono.doOnSuccess：有值或空完成都触发
Mono.empty().doOnSuccess(v -> System.out.println("success: " + v)).subscribe();  // success: null
Mono.just(1).doOnSuccess(v -> System.out.println("success: " + v)).subscribe();  // success: 1
```

> **Mono 中的选择**：只关心有值的情况用 `doOnNext`；关心成功完成（含空）用 `doOnSuccess`。

---

### 4.4 defaultIfEmpty

| 维度 | Flux.defaultIfEmpty | Mono.defaultIfEmpty |
|------|---------------------|---------------------|
| 触发条件 | 源只发 onComplete 无 onNext | 源只发 onComplete 无 onNext |
| 发射数量 | 1 个默认值后完成 | 1 个默认值后完成 |
| 行为 | 完全一致 | 完全一致 |

```java
// Flux
Flux.<String>empty().defaultIfEmpty("default").subscribe(System.out::println);  // default

// Mono
Mono.<String>empty().defaultIfEmpty("default").subscribe(System.out::println);  // default
```

> **差异**：仅在于 Flux 可能发出部分元素后才出错（不触发 defaultIfEmpty），而 Mono 至多 1 个元素。

---

### 4.5 retry/retryWhen

| 维度 | Flux.retry / retryWhen | Mono.retry / retryWhen |
|------|------------------------|------------------------|
| 重订阅行为 | 重新订阅上游，**已发出的元素不会重新发出**给现有订阅者 | 重新订阅上游，重新尝试 |
| 已发出元素 | 订阅者可能已收到部分元素，重试后收到后续元素 | 至多 1 元素，重试即重新尝试获取 |
| 副作用 | 重复执行上游可能产生重复副作用 | 同左 |
| 适用场景 | 部分数据处理失败后继续 | 单次操作失败后重试（如 HTTP 请求） |

```java
// Flux.retry：已发出的元素不会重发
AtomicInteger count = new AtomicInteger(0);
Flux.defer(() -> {
    int i = count.incrementAndGet();
    if (i == 1) return Flux.just(1, 2).concatWith(Flux.error(new RuntimeException()));
    return Flux.just(3, 4);
}).retry(1).subscribe(v -> System.out.print(v + " "));
// 输出: 1 2 3 4 （第一次的 1,2 已发出，重试后接着发 3,4）

// Mono.retry：重新尝试整个操作
AtomicInteger monoCount = new AtomicInteger(0);
Mono.defer(() -> {
    int i = monoCount.incrementAndGet();
    if (i < 3) return Mono.error(new RuntimeException("fail"));
    return Mono.just("success");
}).retry(3).subscribe(v -> System.out.println(v));  // success
```

> **注意**：Flux.retry 时已发出的元素不会"回滚"，订阅者会看到重试前后的元素拼接。Mono 因单值语义无此问题。

---

### 4.6 cache

| 维度 | Flux.cache | Mono.cache |
|------|-----------|-----------|
| 缓存内容 | 所有元素 + 完成信号 | 唯一元素或空信号 |
| 首次订阅 | 触发上游订阅 | 触发上游订阅 |
| 后续订阅 | 直接走缓存（不重新订阅上游） | 直接走缓存 |
| TTL 支持 | `cache(ttl)` / `cache(history, ttl)` | `cache(ttl)` / `cache(ttlForValue, ttlForError, ttlForEmpty)` |
| 高级失效 | 无 | `cacheInvalidateIf` / `cacheInvalidateWhen`（Mono 独有） |
| history 限制 | `cache(n)` 限制缓存数量 | 无（单值无需限制） |

```java
// Flux.cache：缓存所有元素
Flux<Integer> cachedFlux = Flux.range(1, 5).cache();
cachedFlux.subscribe(v -> System.out.print(v + " "));  // 1 2 3 4 5（触发上游）
cachedFlux.subscribe(v -> System.out.print(v + " "));  // 1 2 3 4 5（走缓存，不触发上游）

// Mono.cache：缓存唯一值
AtomicInteger callCount = new AtomicInteger(0);
Mono<String> cachedMono = Mono.fromCallable(() -> {
    callCount.incrementAndGet();
    return "expensive";
}).cache();

cachedMono.subscribe(System.out::println);  // expensive（callCount=1）
cachedMono.subscribe(System.out::println);  // expensive（callCount 仍=1，走缓存）
```

> **差异**：Mono.cache 多了 `cacheInvalidateIf` / `cacheInvalidateWhen`，因为单值缓存的失效语义更简单可控；Flux 多元素缓存失效复杂，无对应方法。

---

## 五、Flux ↔ Mono 互转路径

### 5.1 Mono → Flux

| 转换方式 | 方法 | 说明 |
|----------|------|------|
| 直接转换 | `mono.flux()` | 将 Mono 转为 Flux，发出 0 或 1 个元素 |
| 展开为多元素 | `mono.flatMapMany(x -> Flux.just(x, x))` | 将 Mono 唯一元素展开为多元素 Flux |
| 顺序展开 | `mono.flatMapMany(x -> Flux.fromIterable(...))` | 元素展开为 Iterable 后转 Flux |
| 忽略结果切换 | `mono.thenMany(flux)` | 忽略 Mono 结果，完成后切换到 Flux |
| 串联 | `mono.concatWith(flux)` | Mono 完成后串联 Flux，返回 Flux |
| 合并 | `mono.mergeWith(flux)` | Mono 与 Flux 并发合并，返回 Flux |
| 静态转换 | `Flux.from(mono)` | 等价于 `mono.flux()` |

#### 代码示例

```java
Mono<String> mono = Mono.just("hello");

// 1. flux()
Flux<String> f1 = mono.flux();
f1.subscribe(System.out::println);  // hello

// 2. flatMapMany：展开为多元素
Flux<String> f2 = mono.flatMapMany(s -> Flux.just(s, s.toUpperCase(), s.length() + ""));
f2.subscribe(System.out::println);  // hello HELLO 5

// 3. thenMany：忽略 Mono 结果，切换到 Flux
Flux<Integer> f3 = mono.thenMany(Flux.range(1, 3));
f3.subscribe(System.out::println);  // 1 2 3

// 4. concatWith：串联
Flux<String> f4 = mono.concatWith(Flux.just("world"));
f4.subscribe(System.out::println);  // hello world
```

---

### 5.2 Flux → Mono

| 转换方式 | 方法 | 说明 |
|----------|------|------|
| 取第一个元素 | `flux.next()` | 取第一个元素转 Mono，无元素则空 Mono |
| 取最后一个元素 | `flux.last()` / `last(default)` | 取最后一个元素，空源报错或返回默认 |
| 取唯一元素 | `flux.single()` / `single(default)` | 要求恰好一个元素，否则报错 |
| 取至多一个 | `flux.singleOrEmpty()` | 0 或 1 个元素合法，>1 报错 |
| 取指定位置 | `flux.elementAt(n)` / `elementAt(n, default)` | 取第 n 个元素 |
| 收集为 List | `flux.collectList()` | 所有元素收集到 List，返回 Mono\<List\> |
| 收集为 Map | `flux.collectMap(keyExtractor)` | 收集为 Map |
| 收集为 Multimap | `flux.collectMultimap(keyExtractor)` | 收集为 Map\<K, Collection\<V\>\> |
| 收集排序 List | `flux.collectSortedList()` | 收集并排序 |
| 自定义收集 | `flux.collect(Collector)` | 用 Collector 收集 |
| 聚合 | `flux.reduce(aggregator)` / `reduce(seed, agg)` | 聚合为单值 |
| 计数 | `flux.count()` | 元素个数，返回 Mono\<Long\> |
| 判断有无 | `flux.hasElements()` | 是否有任意元素 |
| 判断包含 | `flux.hasElement(value)` | 是否包含指定值 |
| 全部满足 | `flux.all(predicate)` | 是否全部满足 |
| 任一满足 | `flux.any(predicate)` | 是否有元素满足 |
| 忽略元素 | `flux.then()` | 忽略所有元素，仅留完成信号 |
| 忽略元素 | `flux.ignoreElements()` | 忽略元素，返回 Mono\<T\>（仅完成） |
| 静态转换 | `Mono.from(flux)` | 等价于 `flux.next()`（取首元素后取消） |
| 静态转换 | `Mono.fromDirect(flux)` | 不做基数检查（要求调用方确保 Mono 语义） |

#### 代码示例

```java
Flux<Integer> flux = Flux.range(1, 5);

// 1. next()：取第一个
Mono<Integer> m1 = flux.next();
m1.subscribe(System.out::println);  // 1

// 2. last()：取最后一个
Mono<Integer> m2 = flux.last();
m2.subscribe(System.out::println);  // 5

// 3. single()：要求恰好一个
Mono<Integer> m3 = Flux.just(42).single();
m3.subscribe(System.out::println);  // 42
// Flux.range(1, 5).single()  // 抛异常：期望 1 个但收到 5 个

// 4. collectList()：收集为 List
Mono<List<Integer>> m4 = flux.collectList();
m4.subscribe(System.out::println);  // [1, 2, 3, 4, 5]

// 5. reduce()：聚合
Mono<Integer> m5 = flux.reduce(0, Integer::sum);
m5.subscribe(System.out::println);  // 15

// 6. count()：计数
Mono<Long> m6 = flux.count();
m6.subscribe(System.out::println);  // 5

// 7. elementAt()：取指定位置
Mono<Integer> m7 = flux.elementAt(2);
m7.subscribe(System.out::println);  // 3

// 8. all() / any()
Mono<Boolean> m8 = flux.all(i -> i > 0);
m8.subscribe(System.out::println);  // true
Mono<Boolean> m9 = flux.any(i -> i > 4);
m9.subscribe(System.out::println);  // true

// 9. then()：忽略元素
Mono<Void> m10 = flux.then();
m10.subscribe(v -> System.out.println("done"), e -> {}, () -> System.out.println("completed"));
// completed
```

---

## 六、实战选择指南

### 6.1 何时用 Flux / Mono

| 场景 | 推荐 | 原因 |
|------|------|------|
| 查询单个实体（按 ID） | `Mono<Entity>` | 0..1 语义匹配，至多一个结果 |
| 查询列表 | `Flux<Entity>` | 0..N 语义匹配，多结果流式处理 |
| 写入操作（增删改） | `Mono<Void>` 或 `Mono<Integer>` | 只关心成功/失败或影响行数 |
| 聚合查询（count/sum） | `Mono<Long>` / `Mono<BigDecimal>` | 聚合结果是单值 |
| 是否存在查询（exists） | `Mono<Boolean>` | 布尔单值 |
| 分页查询 | `Flux<Entity>` | 多结果流 |
| 流式处理（如 SSE 推送） | `Flux<Event>` | 持续多元素流 |
| 定时任务 | `Flux<Long>`（interval） | 周期性多事件 |
| 异步单次计算 | `Mono<T>`（fromCallable） | 单值异步 |
| HTTP 请求响应 | `Mono<Response>` | 单次响应 |
| WebSocket 消息流 | `Flux<Message>` | 持续多消息 |
| 缓存读取 | `Mono<T>` | 单值（可能空） |
| 文件行处理 | `Flux<String>` | 多行流式 |

---

### 6.2 常见组合模式

#### 模式 1：Mono → Flux → Mono（查列表再聚合）

```java
// 查询用户列表，然后计算总年龄
Mono<Integer> totalAge = userRepository.findAll()           // Flux<User>
        .map(User::getAge)                                   // Flux<Integer>
        .reduce(0, Integer::sum);                            // Mono<Integer>

totalAge.subscribe(sum -> System.out.println("总年龄: " + sum));
```

#### 模式 2：Flux → Mono → Flux（根据条件决定流）

```java
// 根据配置决定查询哪种数据
Flux<Order> orders = configRepository.isEnabled()           // Mono<Boolean>
        .flatMapMany(enabled -> enabled
                ? orderRepository.findActiveOrders()        // Flux<Order>
                : orderRepository.findAllOrders())          // Flux<Order>
        .onErrorResume(e -> Flux.empty());

orders.subscribe(System.out::println);
```

#### 模式 3：链式调用（Mono.flatMap 链）

```java
// 经典的链式异步调用：查用户 → 查订单 → 查详情
Mono<OrderDetail> detail = userRepository.findById(userId)             // Mono<User>
        .flatMap(user -> orderRepository.findByUser(user.getId()))     // Mono<Order>
        .flatMap(order -> detailRepository.findByOrder(order.getId()));// Mono<OrderDetail>

detail.subscribe(System.out::println);
```

#### 模式 4：并行处理（Flux.flatMap 并发）

```java
// 并发获取多个用户的详情（并发度 10）
Flux<UserDetail> details = Flux.fromIterable(userIds)                  // Flux<String>
        .flatMap(id -> userRepository.findDetail(id), 10)              // Flux<UserDetail>，并发 10
        .doOnError(e -> log.error("获取详情失败", e))
        .onErrorResume(e -> Flux.empty());

details.subscribe(System.out::println);
```

#### 模式 5：错误重试（retryWhen + 指数退避）

```java
// HTTP 请求失败后指数退避重试
Mono<Response> response = webClient.get()
        .uri("/api/data")
        .retrieve()
        .bodyToMono(Response.class)
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))             // 最多 3 次，初始 1s 退避
                .maxBackoff(Duration.ofSeconds(10))                    // 最大退避 10s
                .jitter(0.5))                                          // 抖动 50%
        .onErrorResume(e -> {
            log.error("重试耗尽", e);
            return Mono.just(Response.fallback());
        });

response.subscribe(System.out::println);
```

#### 模式 6：背压控制

```java
// 快生产者 + 慢消费者：使用 boundedElastic 缓冲 + 背压策略
Flux.interval(Duration.ofMillis(10))                                  // 每 10ms 产生一个
        .onBackpressureBuffer(100,                                    // 缓冲 100 个
                buffered -> log.warn("缓冲溢出，丢弃: " + buffered),
                BufferOverflowStrategy.DROP_OLDEST)                   // 满了丢最旧的
        .publishOn(Schedulers.boundedElastic(), 32)                   // 慢消费
        .concatMap(i -> slowProcess(i))                              // 串行处理
        .subscribe(v -> System.out.println("processed: " + v));
```

#### 模式 7：Context 传递

```java
// 通过 Context 传递 traceId，全链路可见
Mono<String> processing = Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault("traceId", "unknown");
            log.info("[{}] 开始处理", traceId);
            return Mono.just("result-" + traceId);
        })
        .contextWrite(Context.of("traceId", UUID.randomUUID().toString()))
        .doOnNext(r -> log.info("处理完成: {}", r));

processing.subscribe();
```

#### 模式 8：热数据共享（share/cache）

```java
// 多个订阅者共享同一个昂贵的数据源
Flux<Data> sharedSource = expensiveDataStream().share();              // 共享上游

// 订阅者 A
sharedSource.subscribe(data -> updateUI(data));

// 订阅者 B（后加入，收到之后的数据）
sharedSource.subscribe(data -> logMetric(data));

// cache 示例：缓存配置，所有订阅者共享
Mono<Config> cachedConfig = loadConfig().cache(Duration.ofMinutes(5)); // 缓存 5 分钟
cachedConfig.subscribe(c -> useConfig(c));
cachedConfig.subscribe(c -> useConfigElsewhere(c));  // 走缓存，不重新加载
```

---

### 6.3 反模式（Anti-patterns）

#### 反模式 1：在 flatMap 中阻塞

```java
// ❌ 错误：在 flatMap 中调用阻塞方法
Flux.just(1, 2, 3)
    .flatMap(i -> {
        try {
            Thread.sleep(1000);              // 阻塞！会拖慢整个响应式链
            return Mono.just(i * 2);
        } catch (InterruptedException e) {
            return Mono.error(e);
        }
    });

// ✅ 正确：用 fromCallable + subscribeOn 切换到阻塞线程池
Flux.just(1, 2, 3)
    .flatMap(i -> Mono.fromCallable(() -> {
        Thread.sleep(1000);
        return i * 2;
    }).subscribeOn(Schedulers.boundedElastic()));
```

#### 反模式 2：不订阅就期望执行

```java
// ❌ 错误：忘记 subscribe，什么都不会发生
Mono.fromCallable(() -> saveToDatabase(data));  // 未订阅，saveToDatabase 不会被调用

// ✅ 正确：显式订阅或交给框架
Mono.fromCallable(() -> saveToDatabase(data)).subscribe();
// 或在 WebFlux 中返回给框架：
// @GetMapping("/save") public Mono<String> save() { return Mono.fromCallable(...); }
```

#### 反模式 3：滥用 block()

```java
// ❌ 错误：在响应式链中调用 block() 会阻塞当前线程
Mono.just(1)
    .flatMap(i -> Mono.just(i + blockingCall()))  // blockingCall 内部 block() 会卡死

// ❌ 错误：在 WebFlux 控制器中 block()
@GetMapping("/user")
public User getUser() {
    return userRepository.findById(id).block();  // 阻塞 Netty 线程！
}

// ✅ 正确：直接返回 Mono
@GetMapping("/user")
public Mono<User> getUser() {
    return userRepository.findById(id);
}
```

#### 反模式 4：忽略背压

```java
// ❌ 错误：快源 + 慢消费无背压控制，可能 OOM
Flux.create(sink -> {
    while (true) {
        sink.next(generateData());  // 无限快速产生，下游消费慢会撑爆缓冲
    }
}, FluxSink.OverflowStrategy.BUFFER)  // 默认无界缓冲
    .subscribe(this::slowConsume);

// ✅ 正确：指定溢出策略或限制速率
Flux.create(sink -> { /* ... */ }, FluxSink.OverflowStrategy.DROP)
    .onBackpressureDrop(d -> log.warn("丢弃: " + d))
    .subscribe(this::slowConsume);
```

#### 反模式 5：在 doOnNext 中做副作用而非纯处理

```java
// ❌ 错误：在 doOnNext 中做阻塞副作用
flux.doOnNext(item -> {
    database.save(item);          // 阻塞调用！
    Thread.sleep(100);            // 阻塞！
});

// ✅ 正确：副作用如果是异步操作，应用 flatMap
flux.flatMap(item ->
    Mono.fromCallable(() -> database.save(item))
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn(item)
);
```

#### 反模式 6：混用 subscribeOn 和 publishOn

```java
// ❌ 误解：以为 subscribeOn 影响下游
Flux.range(1, 3)
    .subscribeOn(Schedulers.boundedElastic())  // 只影响源订阅
    .map(i -> i * 2)                            // 仍在订阅线程执行
    .publishOn(Schedulers.parallel())           // 这之后才在 parallel
    .filter(i -> i > 2)
    .subscribe();

// ✅ 正确理解：
// - subscribeOn：最靠近源的那次生效，影响源订阅线程
// - publishOn：影响其下游执行线程，可多次切换
Flux.range(1, 3)
    .subscribeOn(Schedulers.boundedElastic())  // 源在 boundedElastic 订阅
    .map(i -> i * 2)                            // 在 boundedElastic 执行
    .publishOn(Schedulers.parallel())           // 切换到 parallel
    .filter(i -> i > 2)                         // 在 parallel 执行
    .subscribe();
```

---

## 注意事项

1. **文档定位**：本文档聚焦 Flux 与 Mono 的横向对比与核心概念，单方法的完整签名与行为请参考 [Flux.md](./Flux.md) 与 [Mono.md](./Mono.md)。
2. **版本基准**：基于 reactor-core 3.7.19。部分方法在更高版本或有变化（如 `cacheInvalidateIf` 在 Flux 中可能新增）。
3. **方法名差异**：任务列表中提及的 `combineLatestWith`（Flux 无实例方法，用静态 `combineLatest`）、`zipIterableWith`（实际为 `zipWithIterable`）、`debounce`/`throttleFirst`/`throttleLast`（用 `sample`/`sampleFirst`/`sampleTimeout`）在 3.7.19 中命名不同或不存在的，已在各表中标注。
4. **装配时 vs 订阅时**：所有操作符链在 `subscribe()` 调用前不执行任何数据处理逻辑，这是响应式编程的核心原则。
5. **背压是 Flux 的关切点**：Mono 因 0/1 语义天然无需背压控制；Flux 必须考虑背压策略。
6. **线程模型**：`publishOn` 影响下游，可多次切换；`subscribeOn` 影响最上游订阅，多次调用只第一次生效。
7. **Context 传播方向**：从下游（订阅点）向上游（源）传播，`contextWrite` 必须放在读取上下文操作符的下游。
8. **阻塞 API 谨慎使用**：`block`/`blockFirst`/`blockLast`/`toIterable`/`toStream` 仅用于测试或与命令式代码桥接，禁止在响应式链路内部（如 flatMap lambda 中）使用。
9. **错误处理优先级**：优先使用 `onErrorResume`（灵活回退）和 `doFinally`（资源清理）；`onErrorContinue` 是专家级操作符，易泄漏，谨慎使用。
10. **资源管理**：优先使用 `using`/`usingWhen`（资源生命周期）或 `doFinally`（统一处理完成/错误/取消）。
