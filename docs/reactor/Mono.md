# Mono 全量方法分析

> 基于 reactor-core 3.7.19（源码 `reactor/core/publisher/Mono.java`，共 5470 行）

## 概述

`Mono<T>` 是 Reactor 中代表 **0..1 个元素** 的响应式流 `Publisher`。它通过 `onNext` 信号发射至多一个元素，然后以 `onComplete`（成功，可有值也可无值）或单个 `onError`（失败）终止。`onNext` 与 `onError` 的组合是被显式禁止的。

### 设计理念
- **至多一个元素**：API 围绕"0 或 1"语义设计，因此提供了一批 Flux 没有的独有方法（如 `doOnSuccess`、`flatMapMany`、`then`、`thenEmpty`、`thenMany`、`blockOptional`、`thenReturn` 等）。
- **以 `Mono<Void>` 表示纯完成语义**：当 Publisher 不发数据仅完成时使用。
- **约定**：大多数实现期望在 `onNext` 之后立即调用 `onComplete`；`Mono.never()` 是例外（不发任何信号）。

### 与 Flux 的关系
- `Flux` 代表 0..N 元素，`Mono` 代表 0..1 元素。
- `Mono` 可通过 `flux()` 转 `Flux`；`Flux` 可通过 `next()`、`single()`、`elementAt()` 等转 `Mono`，或用 `Mono.from(Publisher)` 包装（取第一个元素后取消源）。
- `Mono.flatMap` 返回 `Mono`，而 `Mono.flatMapMany` 是返回 `Flux` 的别名（可能多于一个元素）。

### 核心特征
- 抽象类，实现 `CorePublisher<T>`。
- 操作符多为 `final` 方法，返回新 `Mono`（不可变链式）。
- 通过 `onAssembly` 钩子支持装配期诊断（如 `checkpoint`）。
- 支持 Fuseable 优化（`Fuseable` 接口的源会走专门的优化实现，如 `MonoMapFuseable`）。

---

## 一、静态工厂方法（创建操作符）

### 1.1 just

```java
public static <T> Mono<T> just(T data)
```
- **功能**：创建一个在装配时即捕获指定元素的 `Mono`，订阅时发射该元素并完成。
- **参数**：`data` —— 唯一要 `onNext` 的元素。
- **返回值**：`Mono<T>`。
- **示例**：
```java
Mono<String> mono = Mono.just("hello");
mono.subscribe(System.out::println); // hello
```

### 1.2 justOrEmpty

```java
public static <T> Mono<T> justOrEmpty(@Nullable Optional<? extends T> data)
public static <T> Mono<T> justOrEmpty(@Nullable T data)
```
- **功能**：若值存在（`Optional.isPresent()` 或对象非 null）则发射，否则完成空 Mono。
- **参数**：`data` —— 可空值或 `Optional`。
- **返回值**：`Mono<T>`。
- **示例**：
```java
Mono<String> m1 = Mono.justOrEmpty(Optional.ofNullable(getName()));
Mono<String> m2 = Mono.justOrEmpty((String) null); // 等同 empty
```

### 1.3 empty

```java
public static <T> Mono<T> empty()
```
- **功能**：创建一个完成但不发射任何元素的 `Mono`。返回单例 `MonoEmpty.instance()`。
- **返回值**：已完成的 `Mono<T>`。

### 1.4 error

```java
public static <T> Mono<T> error(Throwable error)
public static <T> Mono<T> error(Supplier<? extends Throwable> errorSupplier)
```
- **功能**：创建订阅后立即以指定错误终止的 `Mono`。第二个重载通过 `Supplier` 延迟构造异常，每次订阅都调用一次。
- **参数**：`error` —— 错误信号；`errorSupplier` —— 每次订阅调用的异常工厂。
- **返回值**：失败的 `Mono<T>`。
- **示例**：
```java
Mono<String> m = Mono.error(() -> new IllegalStateException("boom"));
```

### 1.5 never

```java
public static <T> Mono<T> never()
```
- **功能**：永不发任何信号（数据/错误/完成），无限运行。返回单例。
- **返回值**：永不完成的 `Mono<T>`。

### 1.6 fromCallable

```java
public static <T> Mono<T> fromCallable(Callable<? extends T> supplier)
```
- **功能**：用 `Callable` 产生值；若结果为 `null` 则完成空 Mono。
- **参数**：`supplier` —— 产生值的 `Callable`。
- **返回值**：`Mono<T>`。
- **示例**：
```java
Mono<String> m = Mono.fromCallable(() -> blockingJdbcQuery());
```

### 1.7 fromSupplier

```java
public static <T> Mono<T> fromSupplier(Supplier<? extends T> supplier)
```
- **功能**：用 `Supplier` 产生值；若结果为 `null` 则完成空 Mono。
- **参数**：`supplier` —— 产生值的 `Supplier`。
- **返回值**：`Mono<T>`。

### 1.8 fromRunnable

```java
public static <T> Mono<T> fromRunnable(Runnable runnable)
```
- **功能**：执行 `Runnable` 后完成空 Mono，常用于副作用封装。
- **参数**：`runnable` —— 在发射完成信号前执行的 `Runnable`。
- **返回值**：`Mono<T>`（无值，仅完成）。

### 1.9 fromFuture

```java
public static <T> Mono<T> fromFuture(CompletableFuture<? extends T> future)
public static <T> Mono<T> fromFuture(CompletableFuture<? extends T> future, boolean suppressCancel)
public static <T> Mono<T> fromFuture(Supplier<? extends CompletableFuture<? extends T>> futureSupplier)
public static <T> Mono<T> fromFuture(Supplier<? extends CompletableFuture<? extends T>> futureSupplier, boolean suppressCancel)
```
- **功能**：从 `CompletableFuture` 产生值（null 则完成空）。取消 Mono 时默认取消 future（`suppressCancel=false`）；`Supplier` 版本延迟到订阅时获取 future。
- **参数**：`future` —— 目标 future；`suppressCancel` —— 是否阻止取消传播；`futureSupplier` —— future 工厂。
- **返回值**：`Mono<T>`。

### 1.10 fromCompletionStage

```java
public static <T> Mono<T> fromCompletionStage(CompletionStage<? extends T> completionStage)
public static <T> Mono<T> fromCompletionStage(Supplier<? extends CompletionStage<? extends T>> stageSupplier)
```
- **功能**：`fromFuture` 的泛化版本，支持任意 `CompletionStage`。若 stage 同时是 `Future`，取消 Mono 会取消 future。`Supplier` 版本延迟到订阅时触发。
- **返回值**：`Mono<T>`。

### 1.11 fromPublisher / from

```java
public static <T> Mono<T> from(Publisher<? extends T> source)
```
- **功能**：将 `Publisher` 以 `Mono` API 暴露，并确保只发射 0 或 1 个元素（源在第一个 `onNext` 后被取消）。若源已是 `Mono` 则直接返回。
- **参数**：`source` —— 源 `Publisher`。
- **返回值**：发射下一个元素的 `Mono<T>`。

### 1.12 fromDirect

```java
public static <I> Mono<I> fromDirect(Publisher<? extends I> source)
```
- **功能**：将 `Publisher` 转 `Mono`，**不做基数检查**（不会在第一个元素后取消源）。这是高级互操作操作符，要求调用方确保源遵循 Mono 语义（只发一个元素）。
- **参数**：`source` —— Mono 兼容的 `Publisher`。
- **返回值**：包装后的 `Mono<I>`。

### 1.13 create

```java
public static <T> Mono<T> create(Consumer<MonoSink<T>> callback)
```
- **功能**：创建一个延迟发射器，适合桥接回调式 API，可信号至多一个值/完成/错误。每个订阅对应一个 `MonoSink`。
- **参数**：`callback` —— 消费 `MonoSink` 的回调。
- **返回值**：`Mono<T>`。
- **示例**：
```java
Mono<String> m = Mono.create(sink -> {
    HttpListener listener = e -> {
        if (e.getResponseCode() >= 400) sink.error(new RuntimeException("Failed"));
        else sink.success(e.getBody());
    };
    client.addListener(listener);
    sink.onDispose(() -> client.removeListener(listener));
});
```

### 1.14 defer

```java
public static <T> Mono<T> defer(Supplier<? extends Mono<? extends T>> supplier)
```
- **功能**：为每个下游 `Subscriber` 通过 `Supplier` 供应一个目标 `Mono` 并订阅之（延迟工厂）。
- **参数**：`supplier` —— `Mono` 工厂。
- **返回值**：延迟的 `Mono<T>`。

### 1.15 deferContextual

```java
public static <T> Mono<T> deferContextual(Function<ContextView, ? extends Mono<? extends T>> contextualMonoFactory)
```
- **功能**：与 `defer` 相同，但 `Function` 会接收当前 `ContextView`，可基于上下文动态构造 Mono。
- **参数**：`contextualMonoFactory` —— 接收 `ContextView` 的 Mono 工厂。
- **返回值**：依据上下文派生实际 `Mono` 的延迟 `Mono<T>`。

### 1.16 using

```java
public static <T, D> Mono<T> using(Callable<? extends D> resourceSupplier,
                                   Function<? super D, ? extends Mono<? extends T>> sourceSupplier,
                                   Consumer<? super D> resourceCleanup,
                                   boolean eager)
public static <T, D> Mono<T> using(Callable<? extends D> resourceSupplier,
                                   Function<? super D, ? extends Mono<? extends T>> sourceSupplier,
                                   Consumer<? super D> resourceCleanup)
public static <T, D extends AutoCloseable> Mono<T> using(Callable<? extends D> resourceSupplier,
                                                          Function<? super D, ? extends Mono<? extends T>> sourceSupplier)
public static <T, D extends AutoCloseable> Mono<T> using(Callable<? extends D> resourceSupplier,
                                                          Function<? super D, ? extends Mono<? extends T>> sourceSupplier,
                                                          boolean eager)
```
- **功能**：为每个订阅者创建资源，从资源派生 Mono，并保证在终止或取消时释放资源。`eager=true` 时在传信号给下游前清理（有值 Mono 时清理发生在传值前）。`AutoCloseable` 重载默认调用 `close()`。
- **参数**：`resourceSupplier` —— 资源工厂；`sourceSupplier` —— 从资源派生 Mono 的函数；`resourceCleanup` —— 清理函数；`eager` —— 是否提前清理。
- **返回值**：新的 `Mono<T>`。

### 1.17 usingWhen

```java
public static <T, D> Mono<T> usingWhen(Publisher<D> resourceSupplier,
                                       Function<? super D, ? extends Mono<? extends T>> resourceClosure,
                                       Function<? super D, ? extends Publisher<?>> asyncCleanup)
public static <T, D> Mono<T> usingWhen(Publisher<D> resourceSupplier,
                                       Function<? super D, ? extends Mono<? extends T>> resourceClosure,
                                       Function<? super D, ? extends Publisher<?>> asyncComplete,
                                       BiFunction<? super D, ? super Throwable, ? extends Publisher<?>> asyncError,
                                       Function<? super D, ? extends Publisher<?>> asyncCancel)
```
- **功能**：从 `Publisher` 生成资源的事务型操作符，支持异步清理。Mono 版本中**所有信号延迟到 Mono 终止且清理 Publisher 完成才下发**：若清理失败，有值 Mono 的值会被丢弃并只发 `onError`。第二个重载可为完成/错误/取消分别指定异步清理。
- **返回值**：基于事务资源的 `Mono<T>`。

### 1.18 delay

```java
public static Mono<Long> delay(Duration duration)
public static Mono<Long> delay(Duration duration, Scheduler timer)
```
- **功能**：创建一个 Mono，按指定 `Duration` 延迟发射 `onNext(0L)` 然后完成。默认在 `Schedulers.parallel()` 上调度。若需求无法及时产生则发 `onError`。
- **参数**：`duration` —— 延迟时长；`timer` —— 时间能力 `Scheduler`。
- **返回值**：`Mono<Long>`（发射 0L）。

### 1.19 delayElement

```java
public final Mono<T> delayElement(Duration delay)
public final Mono<T> delayElement(Duration delay, Scheduler timer)
```
- **功能**：延迟本 Mono 的元素（`onNext` 信号）指定时长；空 Mono 或错误信号**不**被延迟。有值时后续在 `parallel`（或给定）调度器上执行。
- **返回值**：延迟的 `Mono<T>`。

### 1.20 first / firstWithSignal / firstWithValue / or

```java
@Deprecated
public static <T> Mono<T> first(Mono<? extends T>... monos)          // 转发 firstWithSignal
@Deprecated
public static <T> Mono<T> first(Iterable<? extends Mono<? extends T>> monos)
public static <T> Mono<T> firstWithSignal(Mono<? extends T>... monos)
public static <T> Mono<T> firstWithSignal(Iterable<? extends Mono<? extends T>> monos)
public static <T> Mono<T> firstWithValue(Iterable<? extends Mono<? extends T>> monos)
public static <T> Mono<T> firstWithValue(Mono<? extends T> first, Mono<? extends T>... others)
public final Mono<T> or(Mono<? extends T> other)
```
- **功能**：
  - `firstWithSignal`：选取**第一个发射任意信号**（值/空完成/错误）的 Mono 并重放，等价于最快源。
  - `firstWithValue`：选取**第一个发射值**的源；有值源总是"赢"过空源或错误源；若无源能提供值则失败抛 `NoSuchElementException`（其 cause 为复合异常）。
  - `or`：实例方法，发射本 Mono 或另一 Mono 中第一个可用信号，等价于 `firstWithSignal(this, other)`，可链式累积。
  - `first` 已 `@Deprecated`，转发到 `firstWithSignal`。
- **返回值**：`Mono<T>`。

### 1.21 zip / zipWith / zipWhen / when / whenDelayError

```java
// zip 静态：Tuple2~Tuple8 及 combinator 版本
public static <T1, T2> Mono<Tuple2<T1, T2>> zip(Mono<? extends T1> p1, Mono<? extends T2> p2)
public static <T1, T2, O> Mono<O> zip(Mono<? extends T1> p1, Mono<? extends T2> p2, BiFunction<? super T1, ? super T2, ? extends O> combinator)
public static <T1, T2, T3> Mono<Tuple3<T1, T2, T3>> zip(Mono<? extends T1> p1, Mono<? extends T2> p2, Mono<? extends T3> p3)
// ... 直到 Tuple8（p1..p8）
public static <R> Mono<R> zip(final Iterable<? extends Mono<?>> monos, Function<? super Object[], ? extends R> combinator)
public static <R> Mono<R> zip(Function<? super Object[], ? extends R> combinator, Mono<?>... monos)
// zipDelayError：延迟错误版本
public static <T1, T2> Mono<Tuple2<T1, T2>> zipDelayError(Mono<? extends T1> p1, Mono<? extends T2> p2) // ... 到 Tuple8
public static <R> Mono<R> zipDelayError(final Iterable<? extends Mono<?>> monos, Function<? super Object[], ? extends R> combinator)
public static <R> Mono<R> zipDelayError(Function<? super Object[], ? extends R> combinator, Mono<?>... monos)
// zipWith 实例方法
public final <T2> Mono<Tuple2<T, T2>> zipWith(Mono<? extends T2> other)
public final <T2, O> Mono<O> zipWith(Mono<? extends T2> other, BiFunction<? super T, ? super T2, ? extends O> combinator)
// zipWhen 实例方法
public final <T2> Mono<Tuple2<T, T2>> zipWhen(Function<T, Mono<? extends T2>> rightGenerator)
public final <T2, O> Mono<O> zipWhen(Function<T, Mono<? extends T2>> rightGenerator, BiFunction<T, T2, O> combinator)
// when 系列
public static Mono<Void> when(Publisher<?>... sources)
public static Mono<Void> when(final Iterable<? extends Publisher<?>> sources)
public static Mono<Void> whenDelayError(final Iterable<? extends Publisher<?>> sources)
public static Mono<Void> whenDelayError(Publisher<?>... sources)
```
- **功能**：
  - `zip`：所有源都产生元素后聚合为 `Tuple`（或经 combinator 转换）；任一源出错或空完成会取消其他源并立即终止。
  - `zipDelayError`：延迟错误，多个源出错时异常被合并为 suppressed；某源空完成时其他源跑完再空完成。
  - `zipWith`：实例版 zip，与另一 `Mono` 组合。
  - `zipWhen`：用本 Mono 的值生成第二个 Mono，再组合两者结果。
  - `when` / `whenDelayError`：聚合多个 `Publisher`，仅关心完成信号（`Mono<Void>`），任一错误立即取消其他（`whenDelayError` 延迟错误并合并）。
- **返回值**：`zip*` 返回聚合 `Mono`；`when*` 返回 `Mono<Void>`。
- **示例**：
```java
Mono<Tuple2<String, Integer>> z = Mono.zip(getName(), getAge());
Mono<String> z2 = Mono.zip(getName(), getAge(), (n, a) -> n + ":" + a);
Mono<Void> all = Mono.when(task1, task2, task3);
```

---

## 二、转换操作符

### 2.1 map

```java
public final <R> Mono<R> map(Function<? super T, ? extends R> mapper)
```
- **功能**：对发射元素同步应用函数进行转换。
- **返回值**：`Mono<R>`。

### 2.2 mapNotNull

```java
public final <R> Mono<R> mapNotNull(Function<? super T, ? extends R> mapper)
```
- **功能**：同步转换元素，允许返回 `null`；若结果为 null 则完成空 Mono（等价 `map` + 过滤 null，但 null 不是合法值故不能用 filter）。
- **返回值**：`Mono<R>`。

### 2.3 flatMap

```java
public final <R> Mono<R> flatMap(Function<? super T, ? extends Mono<? extends R>> transformer)
```
- **功能**：异步转换元素为另一个 `Mono`（可改变类型），保持至多一个元素语义。
- **返回值**：`Mono<R>`。

### 2.4 flatMapMany

```java
public final <R> Flux<R> flatMapMany(Function<? super T, ? extends Publisher<? extends R>> mapper)
public final <R> Flux<R> flatMapMany(Function<? super T, ? extends Publisher<? extends R>> mapperOnNext,
                                     Function<? super Throwable, ? extends Publisher<? extends R>> mapperOnError,
                                     Supplier<? extends Publisher<? extends R>> mapperOnComplete)
```
- **功能**：将元素转换为 `Publisher` 并将其发射转发到返回的 `Flux`（元素数可能 >1）。第二个重载可对 onNext/onError/onComplete 分别映射。
- **返回值**：`Flux<R>`（Mono 独有，因 Mono 限制为 0/1 元素）。

### 2.5 concatMap

> **3.7.19 中 `Mono` 类无 `concatMap` 方法**。`concatMap` 是 `Flux` 的操作符；Mono 等价语义可使用 `flatMap`（语义上已是顺序拼接，因为 Mono 只有一个元素）或 `flux().concatMap(...)`。

### 2.6 flatMapIterable

```java
public final <R> Flux<R> flatMapIterable(Function<? super T, ? extends Iterable<? extends R>> mapper)
```
- **功能**：将元素转为 `Iterable`，并将其元素转发到返回的 `Flux`。
- **返回值**：`Flux<R>`。

### 2.7 cast

```java
public final <E> Mono<E> cast(Class<E> clazz)
```
- **功能**：将当前 Mono 产出类型强转为目标类型（内部走 `map(clazz::cast)`）。
- **返回值**：`Mono<E>`。

### 2.8 as

```java
public final <P> P as(Function<? super Mono<T>, P> transformer)
```
- **功能**：将本 `Mono` 立即变换为目标类型 `P`（不限于 `Mono`，装配期执行）。
- **返回值**：`P`（任意类型）。

### 2.9 transform

```java
public final <V> Mono<V> transform(Function<? super Mono<T>, ? extends Publisher<V>> transformer)
```
- **功能**：装配期将本 `Mono` 变换为目标 `Mono`（函数在装配时立即执行）。
- **返回值**：`Mono<V>`。
- **示例**：
```java
Function<Mono<String>, Mono<String>> applySchedulers = mono -> mono.subscribeOn(Schedulers.io()).publishOn(Schedulers.parallel());
mono.transform(applySchedulers).map(v -> v * v).subscribe();
```

### 2.10 transformDeferred

```java
public final <V> Mono<V> transformDeferred(Function<? super Mono<T>, ? extends Publisher<V>> transformer)
```
- **功能**：每个 `Subscriber` 订阅时延迟执行变换（内部用 `defer`），实现按订阅的惰性变换。
- **返回值**：`Mono<V>`。

### 2.11 transformDeferredContextual

```java
public final <V> Mono<V> transformDeferredContextual(BiFunction<? super Mono<T>, ? super ContextView, ? extends Publisher<V>> transformer)
```
- **功能**：与 `transformDeferred` 类似，但变换函数额外接收 `ContextView`，可基于上下文做惰性变换。
- **返回值**：`Mono<V>`。

### 2.12 defaultIfEmpty

```java
public final Mono<T> defaultIfEmpty(T defaultV)
```
- **功能**：若本 Mono 完成无数据，则发射默认值。
- **返回值**：`Mono<T>`。

### 2.13 switchIfEmpty

```java
public final Mono<T> switchIfEmpty(Mono<? extends T> alternate)
```
- **功能**：若本 Mono 完成无数据，则切换订阅备选 `Mono`。
- **返回值**：`Mono<T>`。

### 2.14 expand / expandDeep

```java
public final Flux<T> expand(Function<? super T, ? extends Publisher<? extends T>> expander)
public final Flux<T> expand(Function<? super T, ? extends Publisher<? extends T>> expander, int capacityHint)
public final Flux<T> expandDeep(Function<? super T, ? extends Publisher<? extends T>> expander)
public final Flux<T> expandDeep(Function<? super T, ? extends Publisher<? extends T>> expander, int capacityHint)
```
- **功能**：递归展开元素为图并发射所有结果。
  - `expand`：广度优先遍历（BFS）。
  - `expandDeep`：深度优先遍历（DFS）。
- **参数**：`expander` —— 将值扩展为 `Publisher` 的函数；`capacityHint` —— 每层队列容量提示。
- **返回值**：`Flux<T>`。

### 2.15 handle

```java
public final <R> Mono<R> handle(BiConsumer<? super T, SynchronousSink<R>> handler)
```
- **功能**：通过 `BiConsumer` 处理元素，可调用 sink 的 `next`/`error`/`complete`；至多一次 `next`，是 map + filter 的灵活组合体。当 context-propagation 可用且下游上下文非空时，会自动恢复线程局部变量。
- **返回值**：`Mono<R>`。

### 2.16 materialize

```java
public final Mono<Signal<T>> materialize()
```
- **功能**：将 onNext/onError/onComplete 信号转为 `Signal` 实例并发射；错误被物化为 Signal 后停止传播改为 onComplete。
- **返回值**：`Mono<Signal<T>>`。

### 2.17 dematerialize

```java
public final <X> Mono<X> dematerialize()
```
- **功能**：`materialize` 的逆操作，将 `Signal` 还原为真实信号（onNext→onNext，error→onError，complete→onComplete）。
- **返回值**：`Mono<X>`。

### 2.18 flux

```java
public final Flux<T> flux()
```
- **功能**：将本 `Mono` 转为 `Flux` 变体。若源是 `Callable`（非 ScalarCallable）走 `FluxCallable`，否则走 `Flux.from(this)`。
- **返回值**：`Flux<T>`。

---

## 三、过滤操作符

### 3.1 filter

```java
public final Mono<T> filter(final Predicate<? super T> tester)
```
- **功能**：若有值，用谓词测试；为 true 则重放该值，否则完成空 Mono。支持 Discard Support。
- **返回值**：`Mono<T>`。

### 3.2 filterWhen

```java
public final Mono<T> filterWhen(Function<? super T, ? extends Publisher<Boolean>> asyncPredicate)
```
- **功能**：用生成的 `Publisher<Boolean>` 异步测试值；只考虑第一个发射值，true 重放原值，false 或空则丢弃。仅第一个值后被取消（除非是 Mono）。
- **返回值**：`Mono<T>`。

### 3.3 ofType

```java
public final <U> Mono<U> ofType(final Class<U> clazz)
```
- **功能**：用 `Class` 类型测试值；匹配则转型为该类型并传入新 Mono，否则丢弃。等价 `filter(clazz::isAssignableFrom).cast(clazz)`。
- **返回值**：`Mono<U>`。

---

## 四、组合操作符

### 4.1 and

```java
public final Mono<Void> and(Publisher<?> other)
```
- **功能**：合并本 Mono 与另一源的终止信号，返回 void Mono。等价 `when(this, other)`，可链式累积。
- **返回值**：`Mono<Void>`。

### 4.2 then / thenEmpty / thenMany / thenReturn

```java
public final Mono<Void> then()
public final <V> Mono<V> then(Mono<V> other)
public final Mono<Void> thenEmpty(Publisher<Void> other)
public final <V> Flux<V> thenMany(Publisher<V> other)
public final <V> Mono<V> thenReturn(V value)
```
- **功能**：
  - `then()`：忽略本 Mono 载荷，仅保留完成/错误信号（返回 `Mono<Void>`）。
  - `then(Mono<V>)`：本 Mono 完成后播放另一 `Mono`，忽略本元素；错误透传。
  - `thenEmpty(Publisher<Void>)`：本 Mono 完成后等待另一 `Publisher<Void>` 完成。
  - `thenMany(Publisher<V>)`：本 Mono 完成后播放另一 `Publisher`，返回 `Flux`。
  - `thenReturn(V)`：本 Mono 成功完成后发射指定值。
- **返回值**：见签名（`then`/`thenEmpty` 返回 `Mono<Void>` 或 `Mono<V>`，`thenMany` 返回 `Flux<V>`）。

### 4.3 zipWith / zipWhen / mergeWith / concatWith / or / when

```java
public final <T2> Mono<Tuple2<T, T2>> zipWith(Mono<? extends T2> other)
public final <T2, O> Mono<O> zipWith(Mono<? extends T2> other, BiFunction<? super T, ? super T2, ? extends O> combinator)
public final <T2> Mono<Tuple2<T, T2>> zipWhen(Function<T, Mono<? extends T2>> rightGenerator)
public final <T2, O> Mono<O> zipWhen(Function<T, Mono<? extends T2>> rightGenerator, BiFunction<T, T2, O> combinator)
public final Flux<T> mergeWith(Publisher<? extends T> other)
public final Flux<T> concatWith(Publisher<? extends T> other)
public final Mono<T> or(Mono<? extends T> other)
// when / whenDelayError 见一、静态工厂方法
```
- **功能**：
  - `zipWith`：与另一 `Mono` 组合为 `Tuple2`（或经 combinator 转换）。
  - `zipWhen`：用本 Mono 值生成第二个 Mono 再组合。
  - `mergeWith`：与本 Mono 合并发射，元素可能交错，返回 `Flux`。
  - `concatWith`：与本 Mono 顺序拼接（无交错），返回 `Flux`。
  - `or`：发射本 Mono 或另一 Mono 第一个可用信号。
- **返回值**：`zip*` 返回 `Mono`；`mergeWith`/`concatWith` 返回 `Flux`；`or` 返回 `Mono`。

---

## 五、错误处理操作符

### 5.1 onErrorReturn

```java
public final Mono<T> onErrorReturn(final T fallbackValue)
public final <E extends Throwable> Mono<T> onErrorReturn(Class<E> type, T fallbackValue)
public final Mono<T> onErrorReturn(Predicate<? super Throwable> predicate, T fallbackValue)
```
- **功能**：观察到错误时发射一个捕获的回退值。后两者仅在错误匹配给定类型/谓词时回退，其余错误透传。
- **返回值**：`Mono<T>`。

### 5.2 onErrorResume

```java
public final Mono<T> onErrorResume(Function<? super Throwable, ? extends Mono<? extends T>> fallback)
public final <E extends Throwable> Mono<T> onErrorResume(Class<E> type, Function<? super E, ? extends Mono<? extends T>> fallback)
public final Mono<T> onErrorResume(Predicate<? super Throwable> predicate, Function<? super Throwable, ? extends Mono<? extends T>> fallback)
```
- **功能**：出错时用函数根据错误选择回退 `Mono` 并订阅。后两者仅在错误匹配类型/谓词时回退，否则直接抛原错误。
- **返回值**：`Mono<T>`。

### 5.3 onErrorMap

```java
public final Mono<T> onErrorMap(Function<? super Throwable, ? extends Throwable> mapper)
public final Mono<T> onErrorMap(Predicate<? super Throwable> predicate, Function<? super Throwable, ? extends Throwable> mapper)
public final <E extends Throwable> Mono<T> onErrorMap(Class<E> type, Function<? super E, ? extends Throwable> mapper)
```
- **功能**：同步对错误应用函数进行转换。谓词/类型版仅对匹配错误转换，其余透传。
- **返回值**：`Mono<T>`。

### 5.4 onErrorComplete

```java
public final Mono<T> onErrorComplete()
public final Mono<T> onErrorComplete(Class<? extends Throwable> type)
public final Mono<T> onErrorComplete(Predicate<? super Throwable> predicate)
```
- **功能**：将 `onError` 信号替换为 `onComplete`，使序列以完成终止。后两者仅对匹配类型/谓词的错误生效，其余透传。
- **返回值**：`Mono<T>`。

### 5.5 onErrorContinue

```java
public final Mono<T> onErrorContinue(BiConsumer<Throwable, Object> errorConsumer)
public final <E extends Throwable> Mono<T> onErrorContinue(Class<E> type, BiConsumer<Throwable, Object> errorConsumer)
public final <E extends Throwable> Mono<T> onErrorContinue(Predicate<E> errorPredicate, BiConsumer<Throwable, Object> errorConsumer)
```
- **功能**：让上游兼容操作符在错误时丢弃引发错误的元素并继续后续元素。Mono 上提供此操作符主要是为将配置传播给上游 `Flux`；对 Mono 本身意义不大（无后续元素），更推荐 `onErrorResume`。注意此操作符为专家级，可能让行为不清晰，作用范围易泄漏到未预期的库代码。
- **返回值**：`Mono<T>`。

### 5.6 onErrorStop

```java
public final Mono<T> onErrorStop()
```
- **功能**：若下游使用了 `onErrorContinue`，则恢复默认 'STOP' 模式（错误为终止事件）。可用于在子流（如 flatMap 内）覆盖继承的策略。若未用过 `onErrorContinue` 则无效果。
- **返回值**：`Mono<T>`。

### 5.7 retry / retryWhen

```java
public final Mono<T> retry()
public final Mono<T> retry(long numRetries)
public final Mono<T> retryWhen(Retry retrySpec)
```
- **功能**：
  - `retry()`：出错时无限重订阅。
  - `retry(long)`：容忍错误重订阅指定次数（`Long.MAX_VALUE` 视为无限）。
  - `retryWhen(Retry)`：基于 `Retry` 策略生成的伴随 `Publisher` 重订阅，可用 `Retry.max`、`Retry.maxInARow`、`Retry.backoff` 等构建器。伴随终态信号会以同信号终止结果 Mono。
- **返回值**：`Mono<T>`。

### 5.8 doOnError

```java
public final Mono<T> doOnError(Consumer<? super Throwable> onError)
public final <E extends Throwable> Mono<T> doOnError(Class<E> exceptionType, final Consumer<? super E> onError)
public final Mono<T> doOnError(Predicate<? super Throwable> predicate, final Consumer<? super Throwable> onError)
```
- **功能**：出错时先执行回调，再向下游传播 `onError`。后两者仅对匹配类型/谓词的错误触发回调。
- **返回值**：`Mono<T>`。

---

## 六、副作用操作符

### 6.1 doOnNext

```java
public final Mono<T> doOnNext(Consumer<? super T> onNext)
```
- **功能**：成功发射数据时触发回调，先执行回调再向下游传播 `onNext`。
- **返回值**：`Mono<T>`。

### 6.2 doOnEach

```java
public final Mono<T> doOnEach(Consumer<? super Signal<T>> signalConsumer)
```
- **功能**：发射元素、出错或成功完成时都触发回调，事件以 `Signal` 形式传入。Signal 携带 `Context`。常用于监控。
- **返回值**：`Mono<T>`。

### 6.3 doOnSuccess

```java
public final Mono<T> doOnSuccess(Consumer<? super T> onSuccess)
```
- **功能**：Mono 可视为成功完成时触发回调。参数 `null` 表示无数据完成（在 `onComplete` 前执行），`T` 表示有值完成（在 `onNext` 前执行）。**Mono 独有**（Flux 无此方法，因 Flux 不把单值完成视为终态）。
- **返回值**：`Mono<T>`。

### 6.4 doOnSuccessOrError / doAfterSuccessOrError

> **3.7.19 中已移除**。这两个方法在早期版本中存在但已被废弃删除。其语义可由 `doOnEach`、`doOnTerminate`、`doAfterTerminate` 等替代。

### 6.5 doAfterSuccessOrError

> 同上，**3.7.19 中已移除**。

### 6.6 doAfterTerminate

```java
public final Mono<T> doAfterTerminate(Runnable afterTerminate)
```
- **功能**：Mono 终止（成功完成或出错）后触发回调，**信号先传下游再执行**回调。
- **返回值**：`Mono<T>`。

### 6.7 doOnSubscribe

```java
public final Mono<T> doOnSubscribe(Consumer<? super Subscription> onSubscribe)
```
- **功能**：被订阅时（`Subscription` 产生并传给 `Subscriber` 期间）触发回调。不用于捕获 subscription 调用其方法，仅用于副作用监控。
- **返回值**：`Mono<T>`。

### 6.8 doOnRequest

```java
public final Mono<T> doOnRequest(final LongConsumer consumer)
```
- **功能**：收到请求时触发 `LongConsumer`，先执行回调再向父级传播请求。回调中非致命错误不传播。
- **返回值**：`Mono<T>`。

### 6.9 doOnCancel

```java
public final Mono<T> doOnCancel(Runnable onCancel)
```
- **功能**：被取消时触发回调，先执行回调再将取消信号传上游。
- **返回值**：`Mono<T>`。

### 6.10 doOnTerminate

```java
public final Mono<T> doOnTerminate(Runnable onTerminate)
```
- **功能**：Mono 终止时触发（有值完成/空完成/出错）。与 Flux 不同：Mono 发 `onNext` 即意味着完成，所以回调在元素传播**前**执行（与 `doOnSuccess` 一致）。
- **返回值**：`Mono<T>`。

### 6.11 doFirst

```java
public final Mono<T> doFirst(Runnable onFirst)
```
- **功能**：在 Mono **被订阅前**触发回调（订阅信号反向流动时最先生效）。多个 `doFirst` 执行顺序与声明顺序**相反**。比 `doOnSubscribe` 提供更强的"最先"保证。
- **返回值**：`Mono<T>`。

### 6.12 doFinally

```java
public final Mono<T> doFinally(Consumer<SignalType> onFinally)
```
- **功能**：Mono 因任何原因终止（完成/错误/取消）后触发，传入终态 `SignalType`。因信号先传下游再执行回调，连续多个 `doFinally` 以**反向**顺序执行。
- **返回值**：`Mono<T>`。

### 6.13 doOnDiscard

```java
public final <R> Mono<T> doOnDiscard(final Class<R> type, final Consumer<? super R> discardHook)
```
- **功能**：有条件地清理上游操作符丢弃的元素。hook 必须幂等且对目标类型实例安全。调用是累加的，按声明顺序执行。并非所有操作符都支持（需 javadoc 标注 Discard Support）。
- **返回值**：`Mono<T>`。

### 6.14 tap

```java
public final Mono<T> tap(Supplier<SignalListener<T>> simpleListenerGenerator)
public final Mono<T> tap(Function<ContextView, SignalListener<T>> listenerGenerator)
public final Mono<T> tap(SignalListenerFactory<T, ?> listenerFactory)
```
- **功能**：拦截 Reactive Streams 信号并通知有状态的、每订阅一个的 `SignalListener`。`SignalListener` 抛异常会取消订阅并以该异常终止；但 `doFinally`/`doAfterComplete`/`doAfterError` 中的异常会被丢弃。是新一代副作用/监控入口（替代 `doOnEach` 系列 + `metrics`）。当 context-propagation 可用且下游上下文非空时自动恢复线程局部变量。
- **返回值**：`Mono<T>`。

---

## 七、时间操作符

### 7.1 elapsed

```java
public final Mono<Tuple2<Long, T>> elapsed()
public final Mono<Tuple2<Long, T>> elapsed(Scheduler scheduler)
```
- **功能**：将元素映射为 `Tuple2<毫秒数, 数据>`，毫秒数为订阅到第一个 next 信号的耗时（由 `parallel` 或给定 `Scheduler` 测量）。
- **返回值**：`Mono<Tuple2<Long, T>>`。

### 7.2 timestamp

```java
public final Mono<Tuple2<Long, T>> timestamp()
public final Mono<Tuple2<Long, T>> timestamp(Scheduler scheduler)
```
- **功能**：若有值，发射 `Tuple2<当前时钟毫秒, 数据>`（`Scheduler.now(MILLISECONDS)`，默认 `parallel`）。
- **返回值**：`Mono<Tuple2<Long, T>>`。

### 7.3 timed

```java
public final Mono<Timed<T>> timed()
public final Mono<Timed<T>> timed(Scheduler clock)
```
- **功能**：将 `onNext` 封装为 `Timed<T>` 对象，提供纳秒级 `elapsed()`（自订阅起）、`timestamp()`（`Instant` 含纳秒）、`elapsedSinceSubscription()`（对 Mono 同 `elapsed()`）。比 `elapsed`/`timestamp` 更精确且表达力更强。
- **返回值**：`Mono<Timed<T>>`。

### 7.4 delayElement

```java
public final Mono<T> delayElement(Duration delay)
public final Mono<T> delayElement(Duration delay, Scheduler timer)
```
- **功能**：见 1.19。延迟元素信号；空 Mono 与错误不延迟。

### 7.5 delaySubscription

```java
public final Mono<T> delaySubscription(Duration delay)
public final Mono<T> delaySubscription(Duration delay, Scheduler timer)
public final <U> Mono<T> delaySubscription(Publisher<U> subscriptionDelay)
```
- **功能**：延迟对源 Mono 的**订阅**指定时长（或直到另一 `Publisher` 发值/完成）。元素本身不被延迟。
- **返回值**：`Mono<T>`。

### 7.6 delayUntil

```java
public final Mono<T> delayUntil(Function<? super T, ? extends Publisher<?>> triggerProvider)
```
- **功能**：订阅本 Mono，元素到达后用其生成触发 `Publisher`，延迟到该 Publisher 终止才重放元素。连续 `delayUntil` 会被融合，触发器按序生成订阅；源或触发器出错立即向下传播。
- **返回值**：`Mono<T>`。

### 7.7 cache / cacheInvalidateIf / cacheInvalidateWhen

```java
public final Mono<T> cache()
public final Mono<T> cache(Duration ttl)
public final Mono<T> cache(Duration ttl, Scheduler timer)
public final Mono<T> cache(Function<? super T, Duration> ttlForValue,
                           Function<Throwable, Duration> ttlForError,
                           Supplier<Duration> ttlForEmpty)
public final Mono<T> cache(Function<? super T, Duration> ttlForValue,
                           Function<Throwable, Duration> ttlForError,
                           Supplier<Duration> ttlForEmpty,
                           Scheduler timer)
public final Mono<T> cacheInvalidateIf(Predicate<? super T> invalidationPredicate)
public final Mono<T> cacheInvalidateWhen(Function<? super T, Mono<Void>> invalidationTriggerGenerator)
public final Mono<T> cacheInvalidateWhen(Function<? super T, Mono<Void>> invalidationTriggerGenerator,
                                         Consumer<? super T> onInvalidate)
```
- **功能**：
  - `cache()`：转为 hot 源，首次订阅后缓存信号并无限重放。多并发订阅只订阅源一次。
  - `cache(Duration)` / `cache(ttlForValue, ttlForError, ttlForEmpty)`：带 TTL 过期，TTL 可按信号类型决定；过期后下个订阅重新加载。`Long.MAX_VALUE` 毫秒视为永久缓存。
  - `cacheInvalidateIf`：值导向缓存，每次迟到订阅时用谓词校验缓存值，true 则失效并重新订阅源。空完成与错误不被缓存。
  - `cacheInvalidateWhen`：从缓存值生成 `Mono<Void>` 触发器，其完成即失效；重载可在失效时对值执行 `Consumer`。
- **返回值**：`Mono<T>`。

### 7.8 replay

> **3.7.19 中 `Mono` 类无 `replay` 方法**。`replay` 是 `Flux` 的操作符。Mono 的缓存语义由 `cache` 系列提供。

### 7.9 timeout

```java
public final Mono<T> timeout(Duration timeout)
public final Mono<T> timeout(Duration timeout, Mono<? extends T> fallback)
public final Mono<T> timeout(Duration timeout, Scheduler timer)
public final Mono<T> timeout(Duration timeout, @Nullable Mono<? extends T> fallback, Scheduler timer)
public final <U> Mono<T> timeout(Publisher<U> firstTimeout)
public final <U> Mono<T> timeout(Publisher<U> firstTimeout, Mono<? extends T> fallback)
```
- **功能**：若指定时长内（或给定 `Publisher` 发出前）无元素到达，抛 `TimeoutException`（或切换到回退 `Mono`）。`take(Duration)` 类似但超时是完成而非报错。
- **返回值**：`Mono<T>`。

### 7.10 take / takeUntilOther

```java
public final Mono<T> take(Duration duration)
public final Mono<T> take(Duration duration, Scheduler timer)
public final Mono<T> takeUntilOther(Publisher<?> other)
```
- **功能**：给 Mono 一个时限，超时则**完成**（非报错，区别于 `timeout`）。`takeUntilOther` 在另一 `Publisher` 发出前重放源信号，否则完成。
- **返回值**：`Mono<T>`。

---

## 八、聚合/规约操作符

`Mono` 本身只代表 0/1 元素，因此聚合操作符极少。大部分聚合（`reduce`、`count`、`collect`、`collectList` 等）属于 `Flux`，Mono 可通过 `flux()` 转换后使用，或用 `Mono` 的转换式语义实现等价效果。

### 8.1 count / reduce / collect

> **3.7.19 中 `Mono` 类无 `count`、`reduce`、`collect` 方法**。这些是 `Flux` 的聚合操作符。Mono 单元素场景无需聚合；若确需可用 `flux().count()`、`flux().reduce(...)`、`flux().collect(...)` 等。

### 8.2 等价的 Mono 模式
- **单值规约**：Mono 本身就是一个值，直接用 `map`/`flatMap` 即可。
- **多源聚合**：用 `zip`/`zipWith`/`zipWhen` 把多个 Mono 聚合为一个 Tuple 或经 combinator 后的单一结果。
- **是否包含元素**：`hasElement()` 返回 `Mono<Boolean>`。

```java
public final Mono<Boolean> hasElement()
```
- **功能**：发射 `true`（有元素）或 `false`（空完成）。
- **返回值**：`Mono<Boolean>`。

---

## 九、日志/调试操作符

### 9.1 log

```java
public final Mono<T> log()
public final Mono<T> log(@Nullable String category)
public final Mono<T> log(@Nullable String category, Level level, SignalType... options)
public final Mono<T> log(@Nullable String category, Level level, boolean showOperatorLine, SignalType... options)
public final Mono<T> log(Logger logger)
public final Mono<T> log(Logger logger, Level level, boolean showOperatorLine, SignalType... options)
```
- **功能**：观察所有 Reactive Streams 信号并用 `Logger` 输出。默认 `Level.INFO`、`java.util.logging`（SLF4J 可用时优先）。默认分类 `reactor.Mono` + 操作符后缀。`options` 可细粒度过滤信号类型；`showOperatorLine` 捕获栈显示操作符类/行号。`Logger` 重载使用自定义 logger。
- **返回值**：`Mono<T>`。

### 9.2 checkpoint

```java
public final Mono<T> checkpoint()
public final Mono<T> checkpoint(String description)
public final Mono<T> checkpoint(@Nullable String description, boolean forceStackTrace)
```
- **功能**：激活装配回溯。`checkpoint()` 含完整栈（开销大）；`checkpoint(String)` 仅加描述标记（轻量）；`checkpoint(String, boolean)` 可选是否强制栈。回溯作为 suppressed exception 附着在错误上，建议放在链尾。
- **返回值**：`Mono<T>`。

### 9.3 tap

见 6.14。`tap` 是新代副作用/监控入口，可配合 `SignalListenerFactory`（如 micrometer 模块）实现指标采集，替代已废弃的 `metrics()`。

### 9.4 metrics（已废弃）

```java
@Deprecated
public final Mono<T> metrics()
```
- **功能**：为序列激活指标（需类路径有 instrumentation facade，否则 no-op）。建议配合 `name`/`tag`。**已废弃**，推荐用 `tap` + `reactor-core-micrometer` 模块。
- **返回值**：`Mono<T>`。

### 9.5 name

```java
public final Mono<T> name(String name)
```
- **功能**：给序列命名，可通过 `Scannable.name()` 检索。名称在装配期对 `tap` 可见，常作为指标前缀。
- **返回值**：`Mono<T>`。

### 9.6 tag

```java
public final Mono<T> tag(String key, String value)
```
- **功能**：以 key/value 给序列打标签，可通过 `Scannable.tags()` 检索为 `Set`。装配期对 `tap` 可见，常用于指标 tag。
- **返回值**：`Mono<T>`。

---

## 十、调度与生命周期

### 10.1 publishOn

```java
public final Mono<T> publishOn(Scheduler scheduler)
```
- **功能**：在指定 `Scheduler` 的 `Worker` 上运行 onNext/onComplete/onError。影响链中下游直到下一个 `publishOn`。常用于"快生产者慢消费者"。
- **返回值**：`Mono<T>`。

### 10.2 subscribeOn

```java
public final Mono<T> subscribeOn(Scheduler scheduler)
```
- **功能**：在指定 `Scheduler` 的 `Worker` 上运行 subscribe/onSubscribe/request。影响链中上游到下一个 `publishOn` 的执行上下文。
- **返回值**：`Mono<T>`。

### 10.3 publish

```java
public final <R> Mono<R> publish(Function<? super Mono<T>, ? extends Mono<? extends R>> transform)
```
- **功能**：在一个函数中共享本 `Mono`，可在函数内多次变换消费而不触发多次上游订阅（多播）。底层 `MonoPublishMulticast`。
- **返回值**：`Mono<R>`。

### 10.4 cache / replay / share

- `cache` 系列：见 7.7。
- `replay`：**3.7.19 中 Mono 无此方法**。
- `share`：

```java
public final Mono<T> share()
```
- **功能**：将本 Mono 变为 hot 任务，首个 `Subscriber` 通过 `subscribe()` 订阅后，后续订阅者共享同一 `Subscription` 与结果。所有订阅者取消后取消源 Mono。类似 `Flux.shareNext()`。
- **返回值**：`Mono<T>`。

### 10.5 cancelOn

```java
public final Mono<T> cancelOn(Scheduler scheduler)
```
- **功能**：使订阅者在指定 `Scheduler` 上发出取消信号。
- **返回值**：`Mono<T>`。

### 10.6 toProcessor

> **3.7.19 中 `Mono` 类无公开的 `toProcessor` 方法**。源码仅在注释中提及"legacy #toProcessor() usage"（与 `share`/`NextProcessor` 相关的内部路径），公开 API 已不提供。

### 10.7 onTerminateDetach

```java
public final Mono<T> onTerminateDetach()
```
- **功能**：在终止或取消时分离子 `Subscriber` 与 `Subscription`，帮助在使用非 reactor `Subscriber` 时避免奇怪的内存残留。
- **返回值**：`Mono<T>`。

---

## 十一、上下文操作

### 11.1 contextWrite

```java
public final Mono<T> contextWrite(ContextView contextToAppend)
public final Mono<T> contextWrite(Function<Context, Context> contextModifier)
```
- **功能**：为上游操作符可见的 `Context` 增量写入值。`Context` 与订阅绑定，从下游（默认空）向上"丰富"后对上游可见。`Function` 版便于用 `Context.put` 写 API 返回新 Context。当 context-propagation 开启时走恢复线程局部的实现。
- **返回值**：`Mono<T>`。

### 11.2 contextCapture

```java
public final Mono<T> contextCapture()
```
- **功能**：当类路径有 context-propagation 库时，在订阅阶段捕获线程局部值并放入上游可见的 `Context`。应尽量放在链尾/订阅点附近。若上游可见 `ContextView` 非空，少数操作符（`handle`、`tap`）会自动恢复上下文快照。库不可用时直接返回本 Mono。
- **返回值**：`Mono<T>`。

### 11.3 deferContextual

见 1.15。延迟到订阅时基于 `ContextView` 构造 Mono。

---

## 十二、订阅与阻塞操作

### 12.1 subscribe（回调式）

```java
public final Disposable subscribe()
public final Disposable subscribe(Consumer<? super T> consumer)
public final Disposable subscribe(@Nullable Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer)
public final Disposable subscribe(@Nullable Consumer<? super T> consumer,
                                  @Nullable Consumer<? super Throwable> errorConsumer,
                                  @Nullable Runnable completeConsumer)
public final Disposable subscribe(@Nullable Consumer<? super T> consumer,
                                  @Nullable Consumer<? super Throwable> errorConsumer,
                                  @Nullable Runnable completeConsumer,
                                  @Nullable Consumer<? super Subscription> subscriptionConsumer)
public final Disposable subscribe(@Nullable Consumer<? super T> consumer,
                                  @Nullable Consumer<? super Throwable> errorConsumer,
                                  @Nullable Runnable completeConsumer,
                                  @Nullable Context initialContext)
```
- **功能**：订阅并请求无界需求。各重载分别只消费 onNext/再处理错误/再处理完成/再处理订阅请求/附带初始 Context。无错误处理器时错误会抛出 `Operators.onErrorDropped`。返回 `Disposable` 用于取消。
- **返回值**：`Disposable`。

### 12.2 subscribe（Subscriber 式）

```java
public final void subscribe(Subscriber<? super T> actual)              // 实现 Publisher
public abstract void subscribe(CoreSubscriber<? super T> actual)      // 内部抽象，绕过 Hooks.onLastOperator
```
- **功能**：用给定 `Subscriber` 订阅。`CoreSubscriber` 版本是内部入口，绕过 `onLastOperator` 点切，支持直接传 `Context`。

### 12.3 subscribeWith

```java
public final <E extends Subscriber<? super T>> E subscribeWith(E subscriber)
```
- **功能**：订阅给定 `Subscriber` 并返回它，便于流式使用富 API 子类。
- **返回值**：`E`（传入的 subscriber）。

### 12.4 block

```java
@Nullable
public T block()
@Nullable
public T block(Duration timeout)
```
- **功能**：订阅并**无限阻塞**直到收到 next 信号；返回值或 null（空完成）；错误抛出原异常（受检异常包装为 `RuntimeException`）。`block(Duration)` 在超时后抛以 `TimeoutException` 为 cause 的 `RuntimeException`。每次调用触发新订阅，可能错过 hot 信号。
- **返回值**：`T` 或 null。

### 12.5 blockOptional

```java
public Optional<T> blockOptional()
public Optional<T> blockOptional(Duration timeout)
```
- **功能**：同 `block`，但返回 `Optional`，空完成返回 `Optional.empty()`，便于用 `Optional.orElseThrow(Supplier)` 把空替换为异常。**Mono 独有**（Flux 多元素无法用 Optional 表达）。
- **返回值**：`Optional<T>`。

### 12.6 toFuture

```java
public final CompletableFuture<T> toFuture()
```
- **功能**：转为 `CompletableFuture`，onNext/onComplete 完成它，onError 失败它。
- **返回值**：`CompletableFuture<T>`。

### 12.7 toProcessor

> **3.7.19 中无公开方法**。见 10.6。

---

## 十三、其他方法

### 13.1 hide

```java
public final Mono<T> hide()
```
- **功能**：隐藏本 Mono 实例身份，阻止基于身份的优化（主要用于诊断）。
- **返回值**：`Mono<T>`。

### 13.2 getPrefetch

> **3.7.19 中 `Mono` 类无 `getPrefetch` 方法**。`getPrefetch` 是 `Flux` 的方法（如 `Flux#getPrefetch()`）。Mono 至多一个元素无需预取。

### 13.3 repeat / repeatWhen / repeatWhenEmpty

```java
public final Flux<T> repeat()
public final Flux<T> repeat(BooleanSupplier predicate)
public final Flux<T> repeat(long numRepeat)
public final Flux<T> repeat(long numRepeat, BooleanSupplier predicate)
public final Flux<T> repeatWhen(Function<Flux<Long>, ? extends Publisher<?>> repeatFactory)
public final Mono<T> repeatWhenEmpty(Function<Flux<Long>, ? extends Publisher<?>> repeatFactory)
public final Mono<T> repeatWhenEmpty(int maxRepeat, Function<Flux<Long>, ? extends Publisher<?>> repeatFactory)
```
- **功能**：基于完成信号重复订阅。
  - `repeat()`：无限重复。
  - `repeat(BooleanSupplier)`：谓词为 true 时重复。
  - `repeat(long)`：重复指定次数（`numRepeat+1` 次总订阅，0 表示仅原序列）。
  - `repeat(long, BooleanSupplier)`：带最大次数与谓词。
  - `repeatWhen(Function)`：用伴随 `Flux<Long>`（每次完成发元素数 0/1）工厂控制重复。
  - `repeatWhenEmpty`：当 Mono **空完成**时重订阅，返回 `Mono<T>`；`maxRepeat` 超出抛 `IllegalStateException`。
- **返回值**：`repeat*` 返回 `Flux<T>`；`repeatWhenEmpty` 返回 `Mono<T>`。

### 13.4 onTerminateDetach

见 10.7。

### 13.5 single / singleOptional

```java
public final Mono<T> single()
public final Mono<Optional<T>> singleOptional()
```
- **功能**：
  - `single()`：期望恰好一个元素，空源抛 `NoSuchElementException`。Mono 不需要 `single(Object)`（等价 `defaultIfEmpty`）。
  - `singleOptional()`：将元素包装为 `Optional`，空源发空 Optional。
- **返回值**：`Mono<T>` / `Mono<Optional<T>>`。

### 13.6 ignoreElement / ignoreElements

```java
public final Mono<T> ignoreElement()
public static <T> Mono<T> ignoreElements(Publisher<T> source)
```
- **功能**：
  - `ignoreElement()`：忽略 onNext（丢弃），仅传播终止事件。
  - `ignoreElements(source)`：忽略源的所有元素，仅在源完成时完成。
- **返回值**：`Mono<T>`。

### 13.7 hasElement

```java
public final Mono<Boolean> hasElement()
```
- **功能**：发射 `true`（有元素）或 `false`（空完成）。
- **返回值**：`Mono<Boolean>`。

### 13.8 sequenceEqual

```java
public static <T> Mono<Boolean> sequenceEqual(Publisher<? extends T> source1, Publisher<? extends T> source2)
public static <T> Mono<Boolean> sequenceEqual(Publisher<? extends T> source1, Publisher<? extends T> source2, BiPredicate<? super T, ? super T> isEqual)
public static <T> Mono<Boolean> sequenceEqual(Publisher<? extends T> source1, Publisher<? extends T> source2, BiPredicate<? super T, ? super T> isEqual, int prefetch)
```
- **功能**：逐元素比较两个 Publisher 是否相同，发射 `Boolean`。默认用 `Object::equals`。
- **返回值**：`Mono<Boolean>`。

---

## 十四、纵向关联分析

### 14.1 flatMap vs flatMapMany vs then vs thenEmpty vs thenMany

| 操作符 | 返回类型 | 输入处理 | 适用场景 |
|---|---|---|---|
| `flatMap(Function<T, Mono<R>>)` | `Mono<R>` | 元素 → 另一 `Mono`，保持 0/1 语义 | 异步转换 Mono，结果仍为单值 |
| `flatMapMany(Function<T, Publisher<R>>)` | `Flux<R>` | 元素 → `Publisher`，转发其所有发射 | Mono 转 Flux，可能多元素 |
| `then(Mono<V> other)` | `Mono<V>` | 忽略本元素，完成后播放另一 `Mono` | 串行化：先做 A（不关心其值），再做 B |
| `thenEmpty(Publisher<Void> other)` | `Mono<Void>` | 忽略本元素，完成后等待另一 `Publisher<Void>` 完成 | 串行化纯完成语义 |
| `thenMany(Publisher<V> other)` | `Flux<V>` | 忽略本元素，完成后播放另一 `Publisher` | 串行化后接多元素流 |
| `thenReturn(V value)` | `Mono<V>` | 忽略本元素，完成后发射固定值 | 完成后给出常量结果 |
| `then()` | `Mono<Void>` | 忽略本元素与完成值，仅完成 | 仅关心完成时机 |

> **关键区别**：`flatMap*` 处理的是**元素本身**（转换/拆分），`then*` 处理的是**完成信号**（串行拼接，丢弃上游值）。`flatMap` 返回 `Mono`，`flatMapMany`/`thenMany` 返回 `Flux`。

### 14.2 doOnNext vs doOnSuccess vs doOnEach vs doAfterTerminate

| 操作符 | 触发时机 | 回调参数 | 是否对空完成触发 | Mono 独有 |
|---|---|---|---|---|
| `doOnNext` | 发射元素时（传播前） | `T`（元素） | 否 | 否（Flux 也有） |
| `doOnSuccess` | 成功完成时（传播前） | `T` 或 null | 是 | **是**（Flux 无） |
| `doOnEach` | onNext/onError/onComplete | `Signal<T>` | 是 | 否 |
| `doOnTerminate` | 终止时（传播前） | 无（Runnable） | 是 | 否 |
| `doAfterTerminate` | 终止后（传播后） | 无（Runnable） | 是 | 否 |
| `doFinally` | 任意终止（含 cancel，传播后） | `SignalType` | 是 | 否 |

> **核心**：Mono 中 `onNext` 即意味完成，所以 `doOnSuccess` 是 Mono 语义下"成功"的统一回调（有值传值，无值传 null）。`doOnNext` 只在有值时触发。Flux 没有"成功"概念，故无 `doOnSuccess`。

### 14.3 onErrorResume vs onErrorReturn vs onErrorMap vs onErrorComplete

| 操作符 | 行为 | 回退方式 | 适用场景 |
|---|---|---|---|
| `onErrorResume(Function<Throwable, Mono<T>>)` | 切换到回退 `Mono` | 异步：函数返回 `Mono` | 需动态选择回退策略/异步恢复 |
| `onErrorReturn(T)` | 发射固定回退值后完成 | 同步：固定值 | 已知默认值 |
| `onErrorMap(Function<Throwable, Throwable>)` | 转换错误类型后继续抛 | 同步：转换异常 | 错误类型映射/包装业务异常 |
| `onErrorComplete()` | 将错误转为完成（不发射值） | 同步：吞错误 | 忽略错误视为成功 |
| `onErrorContinue(BiConsumer)` | 上游兼容操作符丢弃错误元素继续 | 配置式（传播给上游 Flux） | 主要用于上游 Flux 容错 |

> **核心**：`onErrorResume` 最灵活（异步回退 Mono），`onErrorReturn` 是其同步固定值的特例，`onErrorMap` 不改变流只转换异常，`onErrorComplete` 把错误吞掉变完成。`onErrorContinue` 是配置性操作符，作用于上游而非本 Mono。

### 14.4 retry vs retryWhen

| 操作符 | 重试触发 | 控制方式 | 高级特性 |
|---|---|---|---|
| `retry()` | 任意错误 | 无限重试 | 无 |
| `retry(long numRetries)` | 任意错误 | 固定次数 | 简单计数 |
| `retryWhen(Retry retrySpec)` | 任意错误 | `Retry` 策略生成的伴随 `Publisher` | 指数退避、最大次数、最大连续次数、自定义伴随便略、`RetrySignal` 元数据 |

> **核心**：`retry()`/`retry(n)` 是简单计数重试，无延迟无策略。`retryWhen` 通过 `Retry` 构建器支持 `Retry.max(n)`、`Retry.maxInARow(n)`、`Retry.backoff(n, Duration)` 等退避策略，且伴随流终态会终止结果 Mono。生产环境几乎总用 `retryWhen`。

### 14.5 cache vs replay vs share

| 操作符 | 适用类型 | 缓存行为 | 多订阅者 |
|---|---|---|---|
| `cache()` 系列 | `Mono` | 首次订阅触发源，缓存信号（含完成/错误）并重放；支持 TTL 与失效 | 共享同一缓存值 |
| `replay()` | `Flux`（**Mono 无**） | 缓存历史元素并重放，可控制回放窗口/数量 | 多订阅者各自回放 |
| `share()` | `Mono`/`Flux` | 首个订阅触发 hot 任务，后续共享同一 `Subscription`；全部取消则取消源 | 共享同一结果，但不缓存（取消即结束） |

> **核心**：Mono 中 `cache` 是"订阅一次缓存并重放"，`share` 是"多订阅者共享一次订阅但无缓存"。`replay` 是 Flux 概念，Mono 用 `cache` 实现等价语义。

### 14.6 delayElement vs delaySubscription vs delayUntil

| 操作符 | 延迟对象 | 输入 | 何时生效 |
|---|---|---|---|
| `delayElement(Duration)` | 元素（onNext 信号） | 固定时长 | 元素到达后延迟下发 |
| `delaySubscription(Duration / Publisher)` | 订阅时机 | 时长或触发 Publisher | 延迟订阅源 |
| `delayUntil(Function<T, Publisher>)` | 元素下发 | 由元素生成的触发 Publisher | 元素到达后订阅触发器，待其终止才下发 |

> **核心**：`delayElement` 延迟"发值"，`delaySubscription` 延迟"订阅源"，`delayUntil` 延迟"发值"但触发源动态由值生成。三者组合可分别控制订阅时机与下发时机。

### 14.7 block vs blockOptional vs toFuture

| 操作符 | 返回类型 | 空完成处理 | 错误处理 | 超时 |
|---|---|---|---|---|
| `block()` | `T`（可 null） | 返回 null | 抛原异常 | 无 |
| `block(Duration)` | `T`（可 null） | 返回 null | 抛原异常 | 抛 RuntimeException(TimeoutException) |
| `blockOptional()` | `Optional<T>` | `Optional.empty()` | 抛原异常 | 无 |
| `blockOptional(Duration)` | `Optional<T>` | `Optional.empty()` | 抛原异常 | 抛 RuntimeException(TimeoutException) |
| `toFuture()` | `CompletableFuture<T>` | 完成无值 | 失败 future | 无 |

> **核心**：`block` 系列是同步阻塞消费（测试/胶水代码用），`toFuture` 是异步 `CompletableFuture` 桥接。`blockOptional` 用 `Optional` 显式区分"空"与"无值"，避免 null 歧义。**生产代码应避免 block，改用 `subscribe`/`toFuture` 异步消费**。

### 14.8 Mono 操作符选择决策树

```
需求
├─ 创建 Mono
│   ├─ 已有值 → just / justOrEmpty(可空)
│   ├─ 无值 → empty / never
│   ├─ 错误 → error
│   ├─ 同步计算 → fromCallable / fromSupplier
│   ├─ 副作用 → fromRunnable
│   ├─ Future → fromFuture / fromCompletionStage
│   ├─ 回调 API → create
│   ├─ 每订阅新实例 → defer / deferContextual
│   ├─ 资源 → using / usingWhen
│   └─ 多源 → zip / when / firstWithSignal / firstWithValue
├─ 转换
│   ├─ 同步 1→1 → map / mapNotNull
│   ├─ 异步 1→0/1 → flatMap
│   ├─ 1→N → flatMapMany / flatMapIterable / expand / expandDeep / flux
│   └─ 复杂 → handle / transform / transformDeferred / as
├─ 过滤 → filter / filterWhen / ofType
├─ 默认值/备选 → defaultIfEmpty / switchIfEmpty
├─ 错误处理
│   ├─ 回退值 → onErrorReturn
│   ├─ 回退 Mono → onErrorResume
│   ├─ 转换异常 → onErrorMap
│   ├─ 忽略错误 → onErrorComplete
│   └─ 重试 → retry / retryWhen(推荐)
├─ 串行拼接 → then / thenEmpty / thenMany / thenReturn / and
├─ 并行组合 → zip / zipWith / zipWhen / mergeWith / concatWith
├─ 选取最快 → firstWithSignal / firstWithValue / or
├─ 副作用 → doOnNext / doOnSuccess / doOnError / doOnEach / doFinally / doFirst / tap
├─ 调度 → publishOn(下游线程) / subscribeOn(上游线程) / cancelOn
├─ 时间 → delayElement / delaySubscription / delayUntil / timeout / take / elapsed / timestamp / timed
├─ 缓存 → cache / cacheInvalidateIf / cacheInvalidateWhen / share
├─ 重复 → repeat / repeatWhen / repeatWhenEmpty
├─ 上下文 → contextWrite / contextCapture / deferContextual / transformDeferredContextual
├─ 调试 → log / checkpoint / name / tag / tap
└─ 消费
    ├─ 异步 → subscribe / subscribeWith
    └─ 阻塞 → block / blockOptional / toFuture
```

---

## 注意事项

1. **Mono 独有方法**：`doOnSuccess`、`flatMapMany`、`then`、`thenEmpty`、`thenMany`、`thenReturn`、`blockOptional`、`single`（返回 Mono）、`hasElement`、`and` 等是 Mono 特有，因 Mono 0/1 语义下"单值完成即成功"、"转多元素需 Flux"、"阻塞返回 Optional"等概念在 Flux 中不成立。
2. **3.7.19 已移除的方法**：`doOnSuccessOrError`、`doAfterSuccessOrError`、`toProcessor`（公开版）、`concatMap`、`getPrefetch`、`replay`、`count`、`reduce`、`collect` 在本版本 `Mono` 中**不存在**，分别属于早期版本或 `Flux`。文档中以 `>` 引用块标注。
3. **已废弃方法**：`first(...)`（转发 `firstWithSignal`）、`metrics()`（推荐 `tap` + micrometer 模块）。
4. **Fuseable 优化**：当源实现 `Fuseable` 时，多个操作符（`map`、`filter`、`handle`、`log`、`doOnEach`、`tap` 等）走专门的 Fuseable 实现（如 `MonoMapFuseable`），减少装箱开销。
5. **context-propagation 集成**：`contextCapture`、`handle`、`tap`、`contextWrite` 在 context-propagation 库可用且上下文非空时会自动恢复线程局部变量。
6. **Discard Support**：标注 "Discard Support" 的操作符（如 `filter`、`filterWhen`、`flatMapIterable`、`then`、`thenEmpty`、`thenMany`、`ignoreElements`、`usingWhen`）在取消/错误时会丢弃元素，可配合 `doOnDiscard` 做清理。
