# Flux 全量方法分析

> 基于 reactor-core 3.7.19

## 概述

`Flux` 是 Reactor 中代表 `0..N` 个元素的响应式流，实现了 Reactive Streams 的 `Publisher` 接口。它会发射 0 到 N 个元素，然后完成（成功或失败）。

### 设计理念

- **声明式 API**：通过链式操作符组合来表达数据流处理逻辑，而非命令式控制流。
- **不可变流水线**：每个操作符返回新的 `Flux`，原始流不被修改，便于组合与复用。
- **背压感知**：遵循 Reactive Streams 契约，下游通过 `request(n)` 控制上游速率，避免被压垮。
- **装配时 vs 订阅时**：链式调用属于"装配阶段"（assembly time），仅描述处理图；真正数据流动发生在"订阅阶段"（subscription time）。`defer`/`deferContextual` 等显式延迟装配。
- **Context 传播**：通过 `ContextView` 在上游链中传递只读上下文（替代 `ThreadLocal`），`contextWrite`/`deferContextual` 支撑该机制。
- **函数式风格**：操作符大量使用 `java.util.function` 接口，但官方建议避免在 lambda 中持有可变状态（可能被多个 Subscriber 共享）。
- **0/1 元素优先用 Mono**：当确定上游只发射 0 或 1 个元素时，应使用 `Mono` 而非 `Flux`。

### 核心特征

| 特征 | 说明 |
|------|------|
| 元素数量 | 0..N |
| 终止信号 | `onComplete`（成功）或 `onError`（失败） |
| 背压 | 通过 `Subscription.request(n)` 协调 |
| 线程模型 | 默认不切换线程，由 `publishOn`/`subscribeOn` 控制 |
| 可订阅性 | 可被多次订阅（除非是热源） |

---

## 一、静态工厂方法（创建操作符）

### 1.1 just

**方法签名**

```java
public static <T> Flux<T> just(T data)
public static <T> Flux<T> just(T... data)
```

**功能描述**：创建一个发射指定元素（单个或多个）的 `Flux`，发射完成后立即 `onComplete`。

**参数说明**
- `data`：要发射的元素，不可为 null（单个元素不可为 null；可变参数不支持 null 元素）。

**返回值**：发射给定元素后完成的 `Flux`。

**示例代码**

```java
Flux<String> flux = Flux.just("A", "B", "C");
flux.subscribe(System.out::println);
// 输出: A B C
```

---

### 1.2 fromArray

**方法签名**

```java
public static <T> Flux<T> fromArray(T[] array)
```

**功能描述**：从数组创建一个 `Flux`，按顺序发射数组所有元素后完成。

**参数说明**
- `array`：源数组，不能为 null。

**返回值**：发射数组元素的 `Flux`。

```java
Integer[] arr = {1, 2, 3};
Flux.fromArray(arr).subscribe(System.out::println);
```

---

### 1.3 fromIterable

**方法签名**

```java
public static <T> Flux<T> fromIterable(Iterable<? extends T> it)
```

**功能描述**：从 `Iterable` 创建 `Flux`，遍历并发射所有元素。

**参数说明**
- `it`：源 `Iterable`，不能为 null。

**返回值**：发射迭代器元素的 `Flux`。

```java
Flux.fromIterable(Arrays.asList("a", "b", "c")).subscribe(System.out::println);
```

---

### 1.4 fromStream

**方法签名**

```java
public static <T> Flux<T> fromStream(Stream<? extends T> s)
public static <T> Flux<T> fromStream(Supplier<Stream<? extends T>> streamSupplier)
```

**功能描述**：从 `java.util.stream.Stream` 创建 `Flux`。Supplier 版本在每次订阅时创建新的 Stream（支持多次订阅）。

**参数说明**
- `s`：源 `Stream`（仅可订阅一次，订阅后会被关闭）。
- `streamSupplier`：每次订阅时调用的 Stream 供应者（支持多次订阅）。

**返回值**：发射流元素的 `Flux`。

```java
Flux.fromStream(() -> Stream.of(1, 2, 3)).subscribe(System.out::println);
```

---

### 1.5 from

**方法签名**

```java
public static <T> Flux<T> from(Publisher<? extends T> source)
```

**功能描述**：将任意 `Publisher` 转换为 `Flux`。如果已是 `Flux` 则原样返回（装饰）。

**参数说明**
- `source`：源 `Publisher`。

**返回值**：包装后的 `Flux`。

```java
Flux.from(Mono.just("x")).subscribe(System.out::println);
```

---

### 1.6 range

**方法签名**

```java
public static Flux<Integer> range(int start, int count)
```

**功能描述**：发射从 `start` 开始的 `count` 个连续 `Integer`（递增 1）。

**参数说明**
- `start`：起始值（含）。
- `count`：发射元素个数，必须 ≥ 0。

**返回值**：发射 `count` 个整数的 `Flux`。

```java
Flux.range(1, 5).subscribe(System.out::println); // 1 2 3 4 5
```

---

### 1.7 empty

**方法签名**

```java
public static <T> Flux<T> empty()
```

**功能描述**：创建一个只发射 `onComplete` 信号的空 `Flux`。

**返回值**：立即完成的空 `Flux`。

```java
Flux<String> empty = Flux.empty();
```

---

### 1.8 error

**方法签名**

```java
public static <T> Flux<T> error(Throwable error)
public static <T> Flux<T> error(Supplier<? extends Throwable> errorSupplier)
public static <O> Flux<O> error(Throwable throwable, boolean whenRequested)
```

**功能描述**：创建一个只发射 `onError` 信号的 `Flux`。Supplier 版本延迟生成错误；`whenRequested=true` 时在下游 `request` 时才触发错误（而非订阅时）。

**参数说明**
- `error`/`errorSupplier`：要发射的错误。
- `whenRequested`：是否延迟到 request 时触发。

**返回值**：仅触发 `onError` 的 `Flux`。

```java
Flux.error(new RuntimeException("boom")).subscribe(System.out::println, e -> System.err.println(e));
```

---

### 1.9 never

**方法签名**

```java
public static <T> Flux<T> never()
```

**功能描述**：创建一个永远不发射任何信号（不 onNext、不 onComplete、不 onError）的 `Flux`。

**返回值**：永不结束的 `Flux`。

---

### 1.10 create

**方法签名**

```java
public static <T> Flux<T> create(Consumer<? super FluxSink<T>> emitter)
public static <T> Flux<T> create(Consumer<? super FluxSink<T>> emitter, OverflowStrategy backpressure)
```

**功能描述**：以编程方式创建 `Flux`，通过 `FluxSink` 适配非响应式源（多线程安全）。可指定背压溢出策略，默认为 `BUFFER`。适合将回调/API 包装为响应式流。

**参数说明**
- `emitter`：接收 `FluxSink` 的回调，可在其中任意调用 `next`/`error`/`complete`。
- `backpressure`：溢出策略（`BUFFER`/`DROP`/`LATEST`/`ERROR`/`IGNORE`）。

**返回值**：可编程发射的 `Flux`。

```java
Flux.create(sink -> {
    sink.next("a");
    sink.next("b");
    sink.complete();
}).subscribe(System.out::println);
```

---

### 1.11 push

**方法签名**

```java
public static <T> Flux<T> push(Consumer<? super FluxSink<T>> emitter)
public static <T> Flux<T> push(Consumer<? super FluxSink<T>> emitter, OverflowStrategy backpressure)
```

**功能描述**：与 `create` 类似，但仅适用于**单线程**生产者（`FluxSink` 非线程安全），开销更小。

**参数说明**
- `emitter`：接收 `FluxSink` 的回调（单线程内调用）。
- `backpressure`：溢出策略。

**返回值**：可编程发射的 `Flux`。

---

### 1.12 generate

**方法签名**

```java
public static <T> Flux<T> generate(Consumer<SynchronousSink<T>> generator)
public static <T, S> Flux<T> generate(Callable<S> stateSupplier, BiFunction<S, SynchronousSink<T>, S> generator)
public static <T, S> Flux<T> generate(Callable<S> stateSupplier, BiFunction<S, SynchronousSink<T>, S> generator, Consumer<? super S> stateConsumer)
```

**功能描述**：以同步、逐个的方式生成元素，每次只能调一次 `next`（`SynchronousSink`）。带状态的版本可维护生成器状态，状态版本还支持清理回调。适合无限或递推序列。

**参数说明**
- `generator`：生成函数，每次调用产出一个元素。
- `stateSupplier`：初始状态供应者。
- `stateConsumer`：终止时清理状态的回调。

**返回值**：按需生成的 `Flux`。

```java
Flux.generate(() -> 0, (state, sink) -> {
    sink.next(state);
    if (state == 3) sink.complete();
    return state + 1;
}).subscribe(System.out::println); // 0 1 2 3
```

---

### 1.13 defer

**方法签名**

```java
public static <T> Flux<T> defer(Supplier<? extends Publisher<T>> supplier)
```

**功能描述**：延迟到订阅时才通过 Supplier 创建真正的 `Flux`。每次订阅都重新调用 Supplier，适合需要"每次订阅新实例"的场景（如读取最新时间/计数）。

**参数说明**
- `supplier`：每次订阅时调用的 `Publisher` 供应者。

**返回值**：延迟装配的 `Flux`。

```java
Flux.defer(() -> Flux.just(System.currentTimeMillis())).subscribe(System.out::println);
```

---

### 1.14 deferContextual

**方法签名**

```java
public static <T> Flux<T> deferContextual(Function<ContextView, ? extends Publisher<T>> contextualPublisherFactory)
```

**功能描述**：延迟到订阅时创建 `Flux`，且能读取下游传播上来的 `ContextView`。是构建需要上下文的动态源的标准方式。

**参数说明**
- `contextualPublisherFactory`：接收 `ContextView` 的工厂函数。

**返回值**：依赖上下文的延迟 `Flux`。

```java
Flux.deferContextual(ctx -> Flux.just(ctx.getOrDefault("key", "default")))
    .contextWrite(Context.of("key", "value"))
    .subscribe(System.out::println); // value
```

---

### 1.15 using

**方法签名**

```java
public static <T, D> Flux<T> using(Callable<? extends D> resourceSupplier,
        Function<? super D, ? extends Publisher<? extends T>> sourceSupplier,
        Consumer<? super D> resourceCleanup)
public static <T, D> Flux<T> using(Callable<? extends D> resourceSupplier,
        Function<? super D, ? extends Publisher<? extends T>> sourceSupplier,
        Consumer<? super D> resourceCleanup, boolean eager)
public static <T, D extends AutoCloseable> Flux<T> using(Callable<? extends D> resourceSupplier,
        Function<? super D, ? extends Publisher<? extends T>> sourceSupplier)
public static <T, D extends AutoCloseable> Flux<T> using(Callable<? extends D> resourceSupplier,
        Function<? super D, ? extends Publisher<? extends T>> sourceSupplier, boolean eager)
```

**功能描述**：以资源安全的方式创建 `Flux`：订阅时获取资源、基于资源创建流、终止/取消时清理资源。`eager=true`（默认）在信号传递前清理，`false` 在信号传递后清理。AutoCloseable 版本自动调用 `close()`。

**参数说明**
- `resourceSupplier`：资源工厂（订阅时调用）。
- `sourceSupplier`：基于资源创建 `Publisher` 的函数。
- `resourceCleanup`：资源清理回调。
- `eager`：是否提前清理。

**返回值**：资源受管的 `Flux`。

```java
Flux.using(() -> new BufferedReader(new FileReader("f")),
        br -> Flux.fromStream(br.lines()),
        br -> br.close())
    .subscribe(System.out::println);
```

---

### 1.16 usingWhen

**方法签名**

```java
public static <T, D> Flux<T> usingWhen(Publisher<D> resourceSupplier,
        Function<? super D, ? extends Publisher<? extends T>> resourceClosure,
        Function<? super D, ? extends Publisher<?>> asyncComplete,
        Function<? super D, ? extends Publisher<?>> asyncError)
public static <T, D> Flux<T> usingWhen(Publisher<D> resourceSupplier,
        Function<? super D, ? extends Publisher<? extends T>> resourceClosure,
        Function<? super D, ? extends Publisher<?>> asyncComplete,
        Function<? super D, ? extends Publisher<?>> asyncError,
        Function<? super D, ? extends Publisher<?>> asyncCancel)
```

**功能描述**：以响应式（异步）方式管理资源。从 `Publisher` 获取资源，正常完成、错误、取消时分别用异步 `Publisher` 清理。常用于事务管理（如 R2DBC 事务）。

**参数说明**
- `resourceSupplier`：资源 `Publisher`。
- `resourceClosure`：基于资源创建数据流。
- `asyncComplete`：正常完成时的异步清理。
- `asyncError`：错误时的异步清理。
- `asyncCancel`：取消时的异步清理（可选）。

**返回值**：异步资源受管的 `Flux`。

---

### 1.17 interval

**方法签名**

```java
public static Flux<Long> interval(Duration period)
public static Flux<Long> interval(Duration delay, Duration period)
public static Flux<Long> interval(Duration period, Scheduler timer)
public static Flux<Long> interval(Duration delay, Duration period, Scheduler timer)
```

**功能描述**：按固定 `period` 周期发射递增的 `Long`（0, 1, 2, ...）。可指定初始延迟和调度器，默认使用 `Schedulers.parallel()`。

**参数说明**
- `delay`：首次发射前的延迟。
- `period`：发射间隔。
- `timer`：时间调度器。

**返回值**：周期发射递增 Long 的 `Flux`（永不完成，需取消）。

```java
Flux.interval(Duration.ofSeconds(1)).take(3).subscribe(System.out::println); // 0 1 2
```

---

### 1.18 concat

**方法签名**

```java
public static <T> Flux<T> concat(Iterable<? extends Publisher<? extends T>> sources)
public static <T> Flux<T> concat(Publisher<? extends Publisher<? extends T>> sources)
public static <T> Flux<T> concat(Publisher<? extends Publisher<? extends T>> sources, int prefetch)
public static <T> Flux<T> concat(Publisher<? extends T>... sources)
```

**功能描述**：按顺序串联多个源，前一个完成后再订阅下一个，保证顺序且无交错。错误会立即终止整条流。

**参数说明**
- `sources`：要串联的源（可迭代/可变参数/外层 Publisher）。
- `prefetch`：从外层预取数量。

**返回值**：顺序串联的 `Flux`。

```java
Flux.concat(Flux.just(1, 2), Flux.just(3, 4)).subscribe(System.out::println); // 1 2 3 4
```

---

### 1.19 concatDelayError

**方法签名**

```java
public static <T> Flux<T> concatDelayError(Publisher<? extends Publisher<? extends T>> sources)
public static <T> Flux<T> concatDelayError(Publisher<? extends Publisher<? extends T>> sources, int prefetch)
public static <T> Flux<T> concatDelayError(Publisher<? extends Publisher<? extends T>> sources, int prefetch, boolean delayUntilEnd)
public static <T> Flux<T> concatDelayError(Publisher<? extends T>... sources)
```

**功能描述**：与 `concat` 类似，但延迟错误处理：某个源出错不会立即终止，而是继续后续源，最后再统一抛出错误（聚合 suppressed）。

**参数说明**
- `sources`：要串联的源。
- `prefetch`：预取数量。
- `delayUntilEnd`：是否等到所有源结束才抛出错误（true），或源错误后立即停止订阅新源（false）。

**返回值**：延迟错误的串联 `Flux`。

---

### 1.20 merge

**方法签名**

```java
public static <T> Flux<T> merge(Publisher<? extends Publisher<? extends T>> source)
public static <T> Flux<T> merge(Publisher<? extends Publisher<? extends T>> source, int concurrency)
public static <T> Flux<T> merge(Publisher<? extends Publisher<? extends T>> source, int concurrency, int prefetch)
public static <I> Flux<I> merge(Iterable<? extends Publisher<? extends I>> sources)
public static <I> Flux<I> merge(Publisher<? extends I>... sources)
public static <I> Flux<I> merge(int prefetch, Publisher<? extends I>... sources)
```

**功能描述**：并发合并多个源，按各源实际发射顺序交错输出（不保证顺序）。`concurrency` 限制同时订阅的源数量。

**参数说明**
- `source`/`sources`：要合并的源。
- `concurrency`：最大并发订阅数。
- `prefetch`：预取数量。

**返回值**：交错合并的 `Flux`。

```java
Flux.merge(Flux.just(1), Flux.just(2)).subscribe(System.out::println);
```

---

### 1.21 mergeDelayError

**方法签名**

```java
public static <I> Flux<I> mergeDelayError(int prefetch, Publisher<? extends I>... sources)
```

**功能描述**：并发合并多个源，延迟错误：任一源出错不会立即终止，等所有源处理完再抛出聚合错误。

**参数说明**
- `prefetch`：预取数量。
- `sources`：要合并的源。

**返回值**：延迟错误的合并 `Flux`。

---

### 1.22 mergeSequential

**方法签名**

```java
public static <T> Flux<T> mergeSequential(Publisher<? extends Publisher<? extends T>> sources)
public static <T> Flux<T> mergeSequential(Publisher<? extends Publisher<? extends T>> sources, int maxConcurrency, int prefetch)
public static <I> Flux<I> mergeSequential(Publisher<? extends I>... sources)
public static <I> Flux<I> mergeSequential(int prefetch, Publisher<? extends I>... sources)
public static <I> Flux<I> mergeSequential(Iterable<? extends Publisher<? extends I>> sources)
public static <I> Flux<I> mergeSequential(Iterable<? extends Publisher<? extends I>> sources, int maxConcurrency, int prefetch)
```

**功能描述**：并发订阅多个源，但按**订阅顺序**输出结果（内部并发执行，输出顺序保持）。介于 `concat`（完全串行）与 `merge`（完全交错）之间。

**参数说明**
- `sources`：要合并的源。
- `maxConcurrency`：最大并发数。
- `prefetch`：预取数量。

**返回值**：并发执行但顺序输出的 `Flux`。

---

### 1.23 mergeSequentialDelayError

**方法签名**

```java
public static <T> Flux<T> mergeSequentialDelayError(Publisher<? extends Publisher<? extends T>> sources, int maxConcurrency, int prefetch)
public static <I> Flux<I> mergeSequentialDelayError(int prefetch, Publisher<? extends I>... sources)
public static <I> Flux<I> mergeSequentialDelayError(Iterable<? extends Publisher<? extends I>> sources, int maxConcurrency, int prefetch)
```

**功能描述**：`mergeSequential` 的延迟错误版本，源错误被暂存，等所有源结束后再统一抛出。

---

### 1.24 mergeOrdered

**方法签名**

```java
public static <I extends Comparable<? super I>> Flux<I> mergeOrdered(Publisher<? extends I>... sources)
public static <T> Flux<T> mergeOrdered(Comparator<? super T> comparator, Publisher<? extends T>... sources)
public static <T> Flux<T> mergeOrdered(int prefetch, Comparator<? super T> comparator, Publisher<? extends T>... sources)
```

**功能描述**：合并多个**已排序**的源，结果保持整体有序（基于给定比较器，默认自然序）。要求每个源本身已排序。

**参数说明**
- `sources`：已排序的源。
- `comparator`：排序比较器。
- `prefetch`：预取数量。

**返回值**：整体有序的合并 `Flux`。

---

### 1.25 mergePriority

**方法签名**

```java
public static <I extends Comparable<? super I>> Flux<I> mergePriority(Publisher<? extends I>... sources)
public static <T> Flux<T> mergePriority(Comparator<? super T> comparator, Publisher<? extends T>... sources)
public static <T> Flux<T> mergePriority(int prefetch, Comparator<? super T> comparator, Publisher<? extends T>... sources)
```

**功能描述**：并发合并多个源，但每个出队的元素是当前所有源队列头部中"最小"的（按比较器），优先输出优先级高的元素。

**参数说明**
- `sources`：要合并的源。
- `comparator`：优先级比较器。
- `prefetch`：预取数量。

**返回值**：按优先级交错输出的 `Flux`。

---

### 1.26 mergePriorityDelayError

**方法签名**

```java
public static <T> Flux<T> mergePriorityDelayError(int prefetch, Comparator<? super T> comparator, Publisher<? extends T>... sources)
```

**功能描述**：`mergePriority` 的延迟错误版本。

---

### 1.27 mergeComparing

**方法签名**

```java
public static <I extends Comparable<? super I>> Flux<I> mergeComparing(Publisher<? extends I>... sources)
public static <T> Flux<T> mergeComparing(Comparator<? super T> comparator, Publisher<? extends T>... sources)
public static <T> Flux<T> mergeComparing(int prefetch, Comparator<? super T> comparator, Publisher<? extends T>... sources)
```

**功能描述**：与 `mergePriority` 等价（`mergePriority` 是其历史别名，3.7 中保留）。基于比较器合并多个源，输出当前各源头部最小元素。

---

### 1.28 mergeComparingDelayError

**方法签名**

```java
public static <T> Flux<T> mergeComparingDelayError(int prefetch, Comparator<? super T> comparator, Publisher<? extends T>... sources)
```

**功能描述**：`mergeComparing` 的延迟错误版本。

---

### 1.29 zip

**方法签名**

```java
public static <T1, T2, O> Flux<O> zip(Publisher<? extends T1> source1, Publisher<? extends T2> source2, BiFunction<? super T1, ? super T2, ? extends O> combinator)
public static <T1, T2> Flux<Tuple2<T1, T2>> zip(Publisher<? extends T1> source1, Publisher<? extends T2> source2)
public static <T1, T2, T3> Flux<Tuple3<T1, T2, T3>> zip(Publisher<? extends T1> source1, Publisher<? extends T2> source2, Publisher<? extends T3> source3)
public static <T1, T2, T3, T4> Flux<Tuple4<T1, T2, T3, T4>> zip(...)
public static <T1, T2, T3, T4, T5> Flux<Tuple5<...>> zip(...)
public static <T1, T2, T3, T4, T5, T6> Flux<Tuple6<...>> zip(...)
public static <T1..T7> Flux<Tuple7<...>> zip(...)
public static <T1..T8> Flux<Tuple8<...>> zip(...)
public static <O> Flux<O> zip(Iterable<? extends Publisher<?>> sources, Function<? super Object[], ? extends O> combinator)
public static <O> Flux<O> zip(Iterable<? extends Publisher<?>> sources, int prefetch, Function<? super Object[], ? extends O> combinator)
public static <I, O> Flux<O> zip(Publisher<? extends Publisher<? extends I>> sources, Function<? super List<I>, ? extends O> combinator)
public static <I, O> Flux<O> zip(final Function<? super Object[], ? extends O> combinator, Publisher<? extends I>... sources)
public static <TUPLE extends Tuple2, V> Flux<V> zip(Publisher<? extends TUPLE> sources, final Function<? super TUPLE, ? extends V> combinator)
```

**功能描述**：等待所有源各发射一个元素后组合为一组输出，任一源完成则整条流完成（严格对齐）。支持 2~8 元组的便利重载，或用 combinator 自定义组合。

**参数说明**
- `source1..source8`：要 zip 的源。
- `combinator`：组合函数。
- `sources`：源集合/外层 Publisher。
- `prefetch`：预取数量。

**返回值**：组合后元素的 `Flux`。

```java
Flux.zip(Flux.just(1, 2), Flux.just("A", "B"), (n, s) -> n + s)
    .subscribe(System.out::println); // 1A 2B
```

---

### 1.30 combineLatest

**方法签名**

```java
public static <T, V> Flux<V> combineLatest(Function<Object[], V> combinator, Publisher<? extends T>... sources)
public static <T, V> Flux<V> combineLatest(Function<Object[], V> combinator, int prefetch, Publisher<? extends T>... sources)
public static <T1, T2, V> Flux<V> combineLatest(Publisher<? extends T1> source1, Publisher<? extends T2> source2, BiFunction<? super T1, ? super T2, ? extends V> combinator)
public static <T1, T2, T3, V> Flux<V> combineLatest(...)
public static <T1..T4, V> Flux<V> combineLatest(...)
public static <T1..T5, V> Flux<V> combineLatest(...)
public static <T1..T6, V> Flux<V> combineLatest(...)
public static <T, V> Flux<V> combineLatest(Iterable<? extends Publisher<? extends T>> sources, Function<? super Object[], ? extends V> combinator)
public static <T, V> Flux<V> combineLatest(Iterable<? extends Publisher<? extends T>> sources, int prefetch, Function<? super Object[], ? extends V> combinator)
```

**功能描述**：任一源发射新元素时，用所有源的**最新**值组合后输出。与 `zip` 不同：不等待对齐，只要有新值就触发组合。

**参数说明**
- `sources`：源集合/可变参数。
- `combinator`：组合函数（接收各源最新值数组）。
- `prefetch`：预取数量。

**返回值**：基于最新值组合的 `Flux`。

```java
Flux.combineLatest(Flux.just(1, 2), Flux.just("A", "B"), (n, s) -> n + s)
    .subscribe(System.out::println); // 1A 2A 2B (顺序取决于时序)
```

---

### 1.31 switchOnNext

**方法签名**

```java
public static <T> Flux<T> switchOnNext(Publisher<? extends Publisher<? extends T>> mergedPublishers)
public static <T> Flux<T> switchOnNext(Publisher<? extends Publisher<? extends T>> mergedPublishers, int prefetch)
```

**功能描述**：将外层发射的多个 `Publisher` 转为单个 `Flux`，每当外层发射新 Publisher 时，取消旧 Publisher 并订阅新 Publisher（切换）。

**参数说明**
- `mergedPublishers`：外层发射内层 Publisher 的源。
- `prefetch`：预取数量。

**返回值**：切换式合并的 `Flux`。

---

### 1.32 first

**方法签名**

```java
public static <I> Flux<I> first(Publisher<? extends I>... sources)
public static <I> Flux<I> first(Iterable<? extends Publisher<? extends I>> sources)
```

**功能描述**：选择第一个发射信号的源并只转发该源的所有信号（其余被取消）。是 `firstWithSignal` 的简写别名。

**返回值**：最快发出信号的源的 `Flux`。

---

### 1.33 firstWithSignal

**方法签名**

```java
public static <I> Flux<I> firstWithSignal(Publisher<? extends I>... sources)
public static <I> Flux<I> firstWithSignal(Iterable<? extends Publisher<? extends I>> sources)
```

**功能描述**：在多个源中选取**第一个发出任何信号**（onNext/onError/onComplete）的源，并仅转发该源信号，其余取消。

**返回值**：最快发出信号的源的 `Flux`。

---

### 1.34 firstWithValue

**方法签名**

```java
public static <I> Flux<I> firstWithValue(Iterable<? extends Publisher<? extends I>> sources)
public static <I> Flux<I> firstWithValue(Publisher<? extends I> first, Publisher<? extends I>... others)
```

**功能描述**：在多个源中选取**第一个发出 onNext 值**的源并转发，其余取消。与 `firstWithSignal` 区别：仅 onNext 触发选择，空完成或出错的源会被忽略。

**返回值**：第一个发出值的源的 `Flux`。

---

## 二、转换操作符

### 2.1 map

**方法签名**

```java
public final <V> Flux<V> map(Function<? super T, ? extends V> mapper)
```

**功能描述**：对每个元素同步应用转换函数，发射转换后的结果。1:1 转换。

**参数说明**
- `mapper`：同步转换函数，不可返回 null。

**返回值**：转换后的 `Flux`。

```java
Flux.just(1, 2, 3).map(i -> i * 2).subscribe(System.out::println); // 2 4 6
```

---

### 2.2 mapNotNull

**方法签名**

```java
public final <V> Flux<V> mapNotNull(Function<? super T, ? extends V> mapper)
```

**功能描述**：与 `map` 类似，但 mapper 返回 `null` 时跳过该元素（不发射）。适合转换中过滤 null。

**参数说明**
- `mapper`：转换函数，可返回 null。

**返回值**：过滤掉 null 转换结果的 `Flux`。

```java
Flux.just(1, 2, 3).mapNotNull(i -> i % 2 == 0 ? i : null).subscribe(System.out::println); // 2
```

---

### 2.3 flatMap

**方法签名**

```java
public final <R> Flux<R> flatMap(Function<? super T, ? extends Publisher<? extends R>> mapper)
public final <V> Flux<V> flatMap(Function<? super T, ? extends Publisher<? extends V>> mapper, int concurrency)
public final <V> Flux<V> flatMap(Function<? super T, ? extends Publisher<? extends V>> mapper, int concurrency, int prefetch)
public final <R> Flux<R> flatMap(Function<? super T, ? extends Publisher<? extends R>> mapper,
        int concurrency, int prefetch, int... prefetchConcurrency)
```

**功能描述**：对每个元素异步展开为内层 `Publisher`，并发订阅多个内层流，结果按到达顺序交错输出（不保序）。1:N 转换。

**参数说明**
- `mapper`：将元素映射为 `Publisher` 的函数。
- `concurrency`：最大并发订阅内层流数量（默认 256）。
- `prefetch`：内层流预取数量（默认 32）。

**返回值**：展开后交错的 `Flux`。

```java
Flux.just(1, 2).flatMap(i -> Flux.just(i, i * 10)).subscribe(System.out::println); // 1 10 2 20 (顺序不保证)
```

---

### 2.4 flatMapSequential

**方法签名**

```java
public final <R> Flux<R> flatMapSequential(Function<? super T, ? extends Publisher<? extends R>> mapper)
public final <R> Flux<R> flatMapSequential(Function<? super T, ? extends Publisher<? extends R>> mapper, int maxConcurrency)
public final <R> Flux<R> flatMapSequential(Function<? super T, ? extends Publisher<? extends R>> mapper, int maxConcurrency, int prefetch)
```

**功能描述**：与 `flatMap` 类似并发订阅内层流，但结果按**元素原始顺序**输出（先到的元素排队等前面的输出）。等价于 `concatMap` 的并发版本。

**参数说明**
- `mapper`：元素到 `Publisher` 的映射。
- `maxConcurrency`：最大并发数。
- `prefetch`：内层预取。

**返回值**：保持原始顺序的展开 `Flux`。

---

### 2.5 flatMapSequentialDelayError

**方法签名**

```java
public final <R> Flux<R> flatMapSequentialDelayError(Function<? super T, ? extends Publisher<? extends R>> mapper,
        int maxConcurrency, int prefetch)
```

**功能描述**：`flatMapSequential` 的延迟错误版本，错误延迟到所有内层流处理完再抛出。

---

### 2.6 flatMapDelayError

**方法签名**

```java
public final <V> Flux<V> flatMapDelayError(Function<? super T, ? extends Publisher<? extends V>> mapper,
        int concurrency, int prefetch)
```

**功能描述**：`flatMap` 的延迟错误版本，内层流出错不会立即终止，等所有内层流结束后统一抛出。

---

### 2.7 flatMapIterable

**方法签名**

```java
public final <R> Flux<R> flatMapIterable(Function<? super T, ? extends Iterable<? extends R>> mapper)
public final <R> Flux<R> flatMapIterable(Function<? super T, ? extends Iterable<? extends R>> mapper, int prefetch)
```

**功能描述**：将每个元素展开为 `Iterable`，逐个发射其中的元素。是 `flatMap` 的同步 `Iterable` 便利版本。

**参数说明**
- `mapper`：元素到 `Iterable` 的映射。
- `prefetch`：预取数量。

**返回值**：展开 Iterable 的 `Flux`。

```java
Flux.just(1, 2).flatMapIterable(i -> Arrays.asList(i, i + 10)).subscribe(System.out::println); // 1 11 2 12
```

---

### 2.8 concatMap

**方法签名**

```java
public final <V> Flux<V> concatMap(Function<? super T, ? extends Publisher<? extends V>> mapper)
public final <V> Flux<V> concatMap(Function<? super T, ? extends Publisher<? extends V>> mapper, int prefetch)
```

**功能描述**：对每个元素展开为内层 `Publisher`，**严格顺序**订阅（前一个完成才订阅下一个），结果保序且无交错。

**参数说明**
- `mapper`：元素到 `Publisher` 的映射。
- `prefetch`：内层预取。

**返回值**：顺序展开的 `Flux`。

```java
Flux.just(1, 2).concatMap(i -> Flux.just(i, i * 10)).subscribe(System.out::println); // 1 10 2 20
```

---

### 2.9 concatMapDelayError

**方法签名**

```java
public final <V> Flux<V> concatMapDelayError(Function<? super T, ? extends Publisher<? extends V>> mapper)
public final <V> Flux<V> concatMapDelayError(Function<? super T, ? extends Publisher<? extends V>> mapper, int prefetch)
public final <V> Flux<V> concatMapDelayError(Function<? super T, ? extends Publisher<? extends V>> mapper, int prefetch, boolean delayUntilEnd)
```

**功能描述**：`concatMap` 的延迟错误版本，内层流出错后是否继续后续取决于 `delayUntilEnd`。

---

### 2.10 concatMapIterable

**方法签名**

```java
public final <R> Flux<R> concatMapIterable(Function<? super T, ? extends Iterable<? extends R>> mapper)
public final <R> Flux<R> concatMapIterable(Function<? super T, ? extends Iterable<? extends R>> mapper, int prefetch)
```

**功能描述**：与 `flatMapIterable` 类似，但以 concat 语义展开（内部行为相近，命名体现顺序语义）。

---

### 2.11 switchMap

**方法签名**

```java
public final <V> Flux<V> switchMap(Function<? super T, Publisher<? extends V>> fn)
public final <V> Flux<V> switchMap(Function<? super T, Publisher<? extends V>> fn, int prefetch)
```

**功能描述**：对每个元素展开为内层 `Publisher`，每当新元素到达时**取消**旧的内层流，订阅新的。仅保留当前最新内层流的输出。

**参数说明**
- `fn`：元素到 `Publisher` 的映射。
- `prefetch`：内层预取。

**返回值**：切换式展开的 `Flux`。

```java
Flux.range(1, 3).switchMap(i -> Flux.interval(Duration.ofMillis(50)).take(2))
    .subscribe(System.out::println); // 只保留最后一个内层流的结果
```

---

### 2.12 switchOnFirst

**方法签名**

```java
public final <V> Flux<V> switchOnFirst(BiFunction<Signal<? extends T>, Flux<T>, Publisher<? extends V>> transformer)
public final <V> Flux<V> switchOnFirst(BiFunction<Signal<? extends T>, Flux<T>, Publisher<? extends V>> transformer, boolean cancelSourceOnComplete)
```

**功能描述**：在源发射第一个信号时（onNext/onComplete）调用 transformer，可基于该信号决定切换到另一个 `Publisher`。

**参数说明**
- `transformer`：接收第一个信号和当前 `Flux`，返回新 `Publisher`。
- `cancelSourceOnComplete`：切换后的流完成时是否取消源。

**返回值**：基于首信号切换的 `Flux`。

---

### 2.13 scan

**方法签名**

```java
public final Flux<T> scan(BiFunction<T, T, T> accumulator)
public final <A> Flux<A> scan(A initial, BiFunction<A, ? super T, A> accumulator)
```

**功能描述**：累加式归约，每次应用 accumulator 后发射中间结果（发射所有中间值，区别于 `reduce` 只发最终值）。无初始值版本用第一个元素作为初始值。

**参数说明**
- `accumulator`：累加函数。
- `initial`：初始种子。

**返回值**：发射所有中间累加结果的 `Flux`。

```java
Flux.just(1, 2, 3).scan(0, (a, b) -> a + b).subscribe(System.out::println); // 0 1 3 6
```

---

### 2.14 scanWith

**方法签名**

```java
public final <A> Flux<A> scanWith(Supplier<A> initial, BiFunction<A, ? super T, A> accumulator)
```

**功能描述**：与带初始值的 `scan` 类似，但初始值通过 `Supplier` 每次订阅时延迟提供（避免共享状态）。

**参数说明**
- `initial`：初始值供应者。
- `accumulator`：累加函数。

**返回值**：发射中间累加结果的 `Flux`。

---

### 2.15 buffer

**方法签名**

```java
public final Flux<List<T>> buffer()
public final Flux<List<T>> buffer(int maxSize)
public final <C extends Collection<? super T>> Flux<C> buffer(int maxSize, Supplier<C> bufferSupplier)
public final Flux<List<T>> buffer(int maxSize, int skip)
public final <C extends Collection<? super T>> Flux<C> buffer(int maxSize, int skip, Supplier<C> bufferSupplier)
public final Flux<List<T>> buffer(Publisher<?> other)
public final <C extends Collection<? super T>> Flux<C> buffer(Publisher<?> other, Supplier<C> bufferSupplier)
public final Flux<List<T>> buffer(Duration bufferingTimespan)
public final Flux<List<T>> buffer(Duration bufferingTimespan, Duration openBufferEvery)
public final Flux<List<T>> buffer(Duration bufferingTimespan, Scheduler timer)
public final Flux<List<T>> buffer(Duration bufferingTimespan, Duration openBufferEvery, Scheduler timer)
```

**功能描述**：将元素收集到 `List`（或自定义集合）中，按数量/时间/边界 Publisher 分批发射。`maxSize` 指定大小；`skip` 指定步长（可重叠或跳过）；`other` 指定开闭边界。

**参数说明**
- `maxSize`：每个缓冲区最大元素数。
- `skip`：每次开新缓冲区前跳过的元素数。
- `bufferSupplier`：自定义集合供应者。
- `other`：边界触发 `Publisher`。
- `bufferingTimespan`：时间跨度。
- `openBufferEvery`：开新缓冲区间隔。

**返回值**：发射集合的 `Flux`。

```java
Flux.range(1, 5).buffer(2).subscribe(System.out::println); // [1, 2] [3, 4] [5]
```

---

### 2.16 bufferTimeout

**方法签名**

```java
public final Flux<List<T>> bufferTimeout(int maxSize, Duration maxTime)
public final <C extends Collection<? super T>> Flux<C> bufferTimeout(int maxSize, Duration maxTime, Supplier<C> bufferSupplier)
public final Flux<List<T>> bufferTimeout(int maxSize, Duration maxTime, Scheduler timer)
public final <C extends Collection<? super T>> Flux<C> bufferTimeout(int maxSize, Duration maxTime, Scheduler timer, Supplier<C> bufferSupplier)
public final Flux<List<T>> bufferTimeout(int maxSize, Duration maxTime, boolean fairBackpressure)
public final Flux<List<T>> bufferTimeout(int maxSize, Duration maxTime, Scheduler timer, boolean fairBackpressure)
public final <C extends Collection<? super T>> Flux<C> bufferTimeout(int maxSize, Duration maxTime, Scheduler timer, boolean fairBackpressure, Supplier<C> bufferSupplier)
```

**功能描述**：在达到 `maxSize` 或 `maxTime` 超时二者中先到者时发射缓冲区。`fairBackpressure=true` 时尽量遵循下游需求。

**参数说明**
- `maxSize`：缓冲区最大元素数。
- `maxTime`：最大等待时长。
- `timer`：时间调度器。
- `fairBackpressure`：是否公平背压。
- `bufferSupplier`：自定义集合。

**返回值**：按大小或时间分批的 `Flux`。

---

### 2.17 bufferUntil

**方法签名**

```java
public final Flux<List<T>> bufferUntil(Predicate<? super T> predicate)
public final Flux<List<T>> bufferUntil(Predicate<? super T> predicate, boolean cutBefore)
```

**功能描述**：缓冲元素直到谓词匹配，然后发射缓冲区。`cutBefore=true` 时匹配元素分到下一个缓冲区。

**参数说明**
- `predicate`：边界判定谓词。
- `cutBefore`：是否在匹配元素前切分。

**返回值**：按谓词分批的 `Flux`。

---

### 2.18 bufferWhile

**方法签名**

```java
public final Flux<List<T>> bufferWhile(Predicate<? super T> predicate)
```

**功能描述**：当谓词为 true 时持续缓冲，谓词为 false 时发射并开新缓冲区。

---

### 2.19 bufferWhen

**方法签名**

```java
public final <U, V> Flux<List<T>> bufferWhen(Publisher<U> bucketOpening, Function<? super U, ? extends Publisher<V>> closeSelector)
public final <U, V, C extends Collection<? super T>> Flux<C> bufferWhen(Publisher<U> bucketOpening, Function<? super U, ? extends Publisher<V>> closeSelector, Supplier<C> bufferSupplier)
```

**功能描述**：由 `bucketOpening` 发射信号开新缓冲区，由 `closeSelector` 返回的 `Publisher` 发射信号关闭缓冲区。完全由 Publisher 控制开闭。

**返回值**：按开闭信号分批的 `Flux`。

---

### 2.20 bufferUntilChanged

**方法签名**

```java
public final Flux<List<T>> bufferUntilChanged()
public final <V> Flux<List<T>> bufferUntilChanged(Function<? super T, ? extends V> keySelector)
public final <V> Flux<List<T>> bufferUntilChanged(Function<? super T, ? extends V> keySelector, BiPredicate<? super V, ? super V> keyComparator)
```

**功能描述**：当相邻元素的 key 变化时切分缓冲区，将相同 key 的连续元素分到同一缓冲区。

**参数说明**
- `keySelector`：提取比较 key 的函数（默认为元素本身）。
- `keyComparator`：key 比较器（默认 `equals`）。

**返回值**：按 key 变化分批的 `Flux`。

---

### 2.21 window

**方法签名**

```java
public final Flux<Flux<T>> window(int maxSize)
public final Flux<Flux<T>> window(int maxSize, int skip)
public final Flux<Flux<T>> window(Publisher<?> boundary)
public final Flux<Flux<T>> window(Duration windowingTimespan)
public final Flux<Flux<T>> window(Duration windowingTimespan, Duration openWindowEvery)
public final Flux<Flux<T>> window(Duration windowingTimespan, Scheduler timer)
public final Flux<Flux<T>> window(Duration windowingTimespan, Duration openWindowEvery, Scheduler timer)
```

**功能描述**：与 `buffer` 类似，但切分为嵌套的 `Flux<Flux<T>>`，每个窗口是一个独立的 `Flux`，而不是 `List`。`maxSize`/`skip`/时间/边界 Publisher 控制切分。

**返回值**：发射窗口 `Flux` 的 `Flux`。

```java
Flux.range(1, 5).window(2).flatMap(w -> w.collectList()).subscribe(System.out::println); // [1,2] [3,4] [5]
```

---

### 2.22 windowTimeout

**方法签名**

```java
public final Flux<Flux<T>> windowTimeout(int maxSize, Duration maxTime)
public final Flux<Flux<T>> windowTimeout(int maxSize, Duration maxTime, boolean fairBackpressure)
public final Flux<Flux<T>> windowTimeout(int maxSize, Duration maxTime, Scheduler timer)
public final Flux<Flux<T>> windowTimeout(int maxSize, Duration maxTime, Scheduler timer, boolean fairBackpressure)
```

**功能描述**：`bufferTimeout` 的 window 版本，达到大小或超时时切分窗口。

---

### 2.23 windowUntil

**方法签名**

```java
public final Flux<Flux<T>> windowUntil(Predicate<T> boundaryTrigger)
public final Flux<Flux<T>> windowUntil(Predicate<T> boundaryTrigger, boolean cutBefore)
public final Flux<Flux<T>> windowUntil(Predicate<T> boundaryTrigger, boolean cutBefore, int prefetch)
```

**功能描述**：`bufferUntil` 的 window 版本。

---

### 2.24 windowWhile

**方法签名**

```java
public final Flux<Flux<T>> windowWhile(Predicate<T> inclusionPredicate)
public final Flux<Flux<T>> windowWhile(Predicate<T> inclusionPredicate, int prefetch)
```

**功能描述**：`bufferWhile` 的 window 版本。

---

### 2.25 windowWhen

**方法签名**

```java
public final <U, V> Flux<Flux<T>> windowWhen(Publisher<U> bucketOpening, Function<? super U, ? extends Publisher<V>> closeSelector)
```

**功能描述**：`bufferWhen` 的 window 版本。

---

### 2.26 windowUntilChanged

**方法签名**

```java
public final Flux<Flux<T>> windowUntilChanged()
public final <V> Flux<Flux<T>> windowUntilChanged(Function<? super T, ? super V> keySelector)
public final <V> Flux<Flux<T>> windowUntilChanged(Function<? super T, ? extends V> keySelector, BiPredicate<? super V, ? super V> keyComparator)
```

**功能描述**：`bufferUntilChanged` 的 window 版本。

---

### 2.27 groupBy

**方法签名**

```java
public final <K> Flux<GroupedFlux<K, T>> groupBy(Function<? super T, ? extends K> keyMapper)
public final <K> Flux<GroupedFlux<K, T>> groupBy(Function<? super T, ? extends K> keyMapper, int prefetch)
public final <K, V> Flux<GroupedFlux<K, V>> groupBy(Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper)
public final <K, V> Flux<GroupedFlux<K, V>> groupBy(Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper, int prefetch)
```

**功能描述**：按 key 分组，发射 `GroupedFlux<K, V>`（每个分组一个独立 `Flux`，可通过 `key()` 获取分组键）。

**参数说明**
- `keyMapper`：提取分组 key 的函数。
- `valueMapper`：可选的值转换。
- `prefetch`：预取。

**返回值**：发射 `GroupedFlux` 的 `Flux`。

```java
Flux.just(1, 2, 3, 4).groupBy(i -> i % 2 == 0 ? "even" : "odd")
    .flatMap(gf -> gf.collectList().map(l -> gf.key() + ":" + l))
    .subscribe(System.out::println);
```

---

### 2.28 cast

**方法签名**

```java
public final <E> Flux<E> cast(Class<E> clazz)
```

**功能描述**：将元素类型强制转换为指定类型，等价于 `map(clazz::cast)`。

**返回值**：类型转换后的 `Flux`。

---

### 2.29 defaultIfEmpty

**方法签名**

```java
public final Flux<T> defaultIfEmpty(T defaultV)
```

**功能描述**：如果源为空（只发 onComplete 无 onNext），则发射一个默认值后完成。

**返回值**：空时补默认值的 `Flux`。

```java
Flux.empty().defaultIfEmpty("default").subscribe(System.out::println); // default
```

---

### 2.30 switchIfEmpty

**方法签名**

```java
public final Flux<T> switchIfEmpty(Publisher<? extends T> alternate)
```

**功能描述**：如果源为空，则切换订阅备选 `Publisher`。与 `defaultIfEmpty` 区别：备选是 `Publisher`（延迟、多元素）。

**返回值**：空时切换备选源的 `Flux`。

```java
Flux.empty().switchIfEmpty(Flux.just("a", "b")).subscribe(System.out::println); // a b
```

---

### 2.31 as

**方法签名**

```java
public final <P> P as(Function<? super Flux<T>, P> transformer)
```

**功能描述**：将当前 `Flux` 传入 transformer 函数，返回**任意类型**结果（不一定是 `Flux`）。常用于转换为目标 API（如 `as(Mono::from)`）。

**返回值**：transformer 返回值。

---

### 2.32 transform

**方法签名**

```java
public final <V> Flux<V> transform(Function<? super Flux<T>, ? extends Publisher<V>> transformer)
```

**功能描述**：在装配时将当前 `Flux` 传入 transformer，返回新 `Publisher`。用于复用一段操作符组合（共享装配逻辑）。transformer 在装配时只执行一次。

**返回值**：转换后的 `Flux`。

```java
Function<Flux<String>, Flux<String>> upper = f -> f.map(String::toUpperCase);
Flux.just("a", "b").transform(upper).subscribe(System.out::println);
```

---

### 2.33 transformDeferred

**方法签名**

```java
public final <V> Flux<V> transformDeferred(Function<? super Flux<T>, ? extends Publisher<V>> transformer)
```

**功能描述**：与 `transform` 区别：transformer 在**每次订阅时**重新执行（延迟到订阅时），使每个订阅者得到独立的操作符图。适合涉及状态的场景。

**返回值**：每次订阅重新转换的 `Flux`。

---

### 2.34 transformDeferredContextual

**方法签名**

```java
public final <V> Flux<V> transformDeferredContextual(BiFunction<? super Flux<T>, ? super ContextView, ? extends Publisher<V>> transformer)
```

**功能描述**：`transformDeferred` 的上下文版本，订阅时还能读取 `ContextView`，基于上下文动态转换。

**返回值**：依赖上下文的延迟转换 `Flux`。

---

### 2.35 expand

**方法签名**

```java
public final Flux<T> expand(Function<? super T, ? extends Publisher<? extends T>> expander, int capacityHint)
public final Flux<T> expand(Function<? super T, ? extends Publisher<? extends T>> expander)
```

**功能描述**：广度优先（BFS）递归展开，对每个元素应用 expander 产生后续元素，先输出当前层再输出下一层。

**返回值**：广度优先展开的 `Flux`。

---

### 2.36 expandDeep

**方法签名**

```java
public final Flux<T> expandDeep(Function<? super T, ? extends Publisher<? extends T>> expander, int capacityHint)
public final Flux<T> expandDeep(Function<? super T, ? extends Publisher<? extends T>> expander)
```

**功能描述**：深度优先（DFS）递归展开，对每个元素应用 expander 产生后续元素，沿一个分支深入到底再回溯。

**返回值**：深度优先展开的 `Flux`。

---

### 2.37 handle

**方法签名**

```java
public final <R> Flux<R> handle(BiConsumer<? super T, SynchronousSink<R>> handler)
```

**功能描述**：对每个元素调用 handler，handler 通过 `SynchronousSink` 决定输出什么（可 0/1/N 输出，或调用 error/complete）。结合 `map`+`filter` 的灵活版。

**返回值**：经 handler 处理的 `Flux`。

```java
Flux.range(1, 5).handle((v, sink) -> {
    if (v % 2 == 0) sink.next(v * 10);
}).subscribe(System.out::println); // 20 40
```

---

### 2.38 materialize

**方法签名**

```java
public final Flux<Signal<T>> materialize()
```

**功能描述**：将源的所有信号（onNext/onError/onComplete）转换为 `Signal<T>` 对象发射，最后以 onComplete 结束（错误/完成被"物化"为普通元素）。

**返回值**：发射 `Signal` 的 `Flux`。

---

### 2.39 dematerialize

**方法签名**

```java
public final <X> Flux<X> dematerialize()
```

**功能描述**：`materialize` 的逆操作，将 `Signal<T>` 还原为原始信号序列。

**返回值**：还原后的 `Flux`。

---

### 2.40 index

**方法签名**

```java
public final <I> Flux<I> index(BiFunction<? super Long, ? super T, ? extends I> indexMapper)
```

**功能描述**：为每个元素附加从 0 开始的递增索引，通过 indexMapper 组合索引与元素。

**返回值**：带索引的 `Flux`。

```java
Flux.just("a", "b").index((i, v) -> i + ":" + v).subscribe(System.out::println); // 0:a 1:b
```

---

## 三、过滤操作符

### 3.1 filter

**方法签名**

```java
public final Flux<T> filter(Predicate<? super T> p)
```

**功能描述**：只保留满足谓词的元素。

```java
Flux.range(1, 5).filter(i -> i % 2 == 0).subscribe(System.out::println); // 2 4
```

---

### 3.2 filterWhen

**方法签名**

```java
public final Flux<T> filterWhen(Function<? super T, ? extends Publisher<Boolean>> asyncPredicate)
public final Flux<T> filterWhen(Function<? super T, ? extends Publisher<Boolean>> asyncPredicate, int bufferSize)
```

**功能描述**：异步过滤：对每个元素调用 asyncPredicate 得到 `Publisher<Boolean>`，根据其发射的值决定是否保留。适合需要异步判断（如查库）的过滤。

**返回值**：异步过滤后的 `Flux`。

---

### 3.3 distinct

**方法签名**

```java
public final Flux<T> distinct()
public final <V> Flux<T> distinct(Function<? super T, ? extends V> keySelector)
public final <V, C extends Collection<? super V>> Flux<T> distinct(Function<? super T, ? extends V> keySelector, Supplier<C> collectionSupplier)
public final <V, C> Flux<T> distinct(Function<? super T, ? extends V> keySelector, Supplier<C> collectionSupplier, BiPredicate<C, V> collectionPredicate)
```

**功能描述**：去重，只保留首次出现的元素。可通过 keySelector 提取比较 key，可自定义去重集合（如用 `Set` 限制数量）。

**返回值**：去重后的 `Flux`。

```java
Flux.just(1, 2, 2, 3, 1).distinct().subscribe(System.out::println); // 1 2 3
```

---

### 3.4 distinctUntilChanged

**方法签名**

```java
public final Flux<T> distinctUntilChanged()
public final <V> Flux<T> distinctUntilChanged(Function<? super T, ? extends V> keySelector)
public final <V> Flux<T> distinctUntilChanged(Function<? super T, ? extends V> keySelector, BiPredicate<? super V, ? super V> keyComparator)
```

**功能描述**：仅当相邻元素 key 不同时保留（与上一个比），不去除全局重复。

**返回值**：相邻去重的 `Flux`。

```java
Flux.just(1, 1, 2, 2, 1).distinctUntilChanged().subscribe(System.out::println); // 1 2 1
```

---

### 3.5 take

**方法签名**

```java
public final Flux<T> take(long n)
public final Flux<T> take(long n, boolean limitRequest)
public final Flux<T> take(Duration timespan)
public final Flux<T> take(Duration timespan, Scheduler timer)
```

**功能描述**：只取前 N 个或指定时长内的元素，然后取消上游。`limitRequest=true` 时只向上游 request N 个（不超量请求）。

**参数说明**
- `n`：取的元素数。
- `limitRequest`：是否限制 request 量为 n。
- `timespan`：取的时长。

**返回值**：取前 N 个的 `Flux`。

```java
Flux.range(1, 100).take(3).subscribe(System.out::println); // 1 2 3
```

---

### 3.6 takeLast

**方法签名**

```java
public final Flux<T> takeLast(int n)
```

**功能描述**：只发射源的最后 N 个元素（需缓存等待源完成）。

**返回值**：取最后 N 个的 `Flux`。

---

### 3.7 takeUntil

**方法签名**

```java
public final Flux<T> takeUntil(Predicate<? super T> predicate)
```

**功能描述**：发射元素直到谓词匹配（含匹配的那个），然后取消上游。

**返回值**：取到匹配为止的 `Flux`。

---

### 3.8 takeUntilOther

**方法签名**

```java
public final Flux<T> takeUntilOther(Publisher<?> other)
```

**功能描述**：发射元素直到 `other` Publisher 发射信号或终止，然后取消上游。

**返回值**：由 other 控制终止的 `Flux`。

---

### 3.9 takeWhile

**方法签名**

```java
public final Flux<T> takeWhile(Predicate<? super T> continuePredicate)
```

**功能描述**：只要谓词为 true 就持续发射，遇到第一个 false 立即取消上游（不含 false 元素）。

**返回值**：取到首次不满足为止的 `Flux`。

---

### 3.10 skip

**方法签名**

```java
public final Flux<T> skip(long skipped)
public final Flux<T> skip(Duration timespan)
public final Flux<T> skip(Duration timespan, Scheduler timer)
```

**功能描述**：跳过前 N 个元素或指定时长内的元素。

**返回值**：跳过前 N 个的 `Flux`。

```java
Flux.range(1, 5).skip(2).subscribe(System.out::println); // 3 4 5
```

---

### 3.11 skipLast

**方法签名**

```java
public final Flux<T> skipLast(int n)
```

**功能描述**：跳过最后 N 个元素（需缓存）。

**返回值**：跳过最后 N 个的 `Flux`。

---

### 3.12 skipUntil

**方法签名**

```java
public final Flux<T> skipUntil(Predicate<? super T> untilPredicate)
```

**功能描述**：跳过元素直到谓词匹配（含匹配元素开始发射）。

**返回值**：跳到匹配为止的 `Flux`。

---

### 3.13 skipUntilOther

**方法签名**

```java
public final Flux<T> skipUntilOther(Publisher<?> other)
```

**功能描述**：跳过元素直到 `other` Publisher 发射信号，之后开始转发。

**返回值**：由 other 控制开始转发的 `Flux`。

---

### 3.14 skipWhile

**方法签名**

```java
public final Flux<T> skipWhile(Predicate<? super T> skipPredicate)
```

**功能描述**：只要谓词为 true 就跳过，遇到第一个 false 后开始转发剩余所有元素。

**返回值**：跳到首次不满足为止的 `Flux`。

---

### 3.15 limitRate

**方法签名**

```java
public final Flux<T> limitRate(int prefetchRate)
public final Flux<T> limitRate(int highTide, int lowTide)
```

**功能描述**：限制向上游的请求速率，按 `prefetchRate` 批量请求（默认补充 75%）。`highTide`/`lowTide` 控制高/低水位补充策略。

**返回值**：限速的 `Flux`。

---

### 3.16 limitRequest

**方法签名**

```java
public final Flux<T> limitRequest(long n)
```

**功能描述**：限制总请求量为 N，发射满 N 个元素后完成（且不再向上游请求超过 N）。与 `take(n)` 区别：`take` 取够后取消上游，`limitRequest` 只约束 request 总量（上游可能继续）。

**返回值**：总请求受限的 `Flux`。

---

### 3.17 elementAt

**方法签名**

```java
public final Mono<T> elementAt(int index)
public final Mono<T> elementAt(int index, T defaultValue)
```

**功能描述**：只发射第 `index` 个元素（从 0 开始）的 `Mono`。无默认值时越界报 `IndexOutOfBoundsException`；有默认值时越界返回默认值。

**返回值**：第 N 个元素的 `Mono`。

---

### 3.18 last

**方法签名**

```java
public final Mono<T> last()
public final Mono<T> last(T defaultValue)
```

**功能描述**：只发射最后一个元素。空源时无默认值报 `NoSuchElementException`，有默认值返回默认值。

**返回值**：最后一个元素的 `Mono`。

---

### 3.19 ignoreElements

**方法签名**

```java
public final Mono<T> ignoreElements()
```

**功能描述**：忽略所有 onNext，只关心完成/错误信号，返回 `Mono`（仅完成或错误）。

**返回值**：忽略元素的 `Mono`。

---

### 3.20 next

**方法签名**

```java
public final Mono<T> next()
```

**功能描述**：只取第一个元素，转为 `Mono`，然后取消上游。

**返回值**：第一个元素的 `Mono`。

---

### 3.21 single

**方法签名**

```java
public final Mono<T> single()
public final Mono<T> single(T defaultValue)
```

**功能描述**：要求源恰好发射 1 个元素：1 个则发射，0 个报错（或有默认值返回默认值），>1 个报错。

**返回值**：断言单元素的 `Mono`。

---

### 3.22 singleOrEmpty

**方法签名**

```java
public final Mono<T> singleOrEmpty()
```

**功能描述**：要求源发射 0 或 1 个元素：0 个发空 `Mono`，1 个发射该值，>1 个报错。

**返回值**：断言至多一个元素的 `Mono`。

---

### 3.23 sample

**方法签名**

```java
public final Flux<T> sample(Duration timespan)
public final <U> Flux<T> sample(Publisher<U> sampler)
```

**功能描述**：周期性采样最近一个元素（throttleLast 语义）：每个周期结束发射该周期内最新的元素。也可由 sampler Publisher 控制采样点。

**返回值**：采样最新值的 `Flux`。

```java
Flux.range(1, 1000).sample(Duration.ofMillis(100)).subscribe(System.out::println);
```

---

### 3.24 sampleFirst

**方法签名**

```java
public final Flux<T> sampleFirst(Duration timespan)
public final <U> Flux<T> sampleFirst(Function<? super T, ? extends Publisher<U>> samplerFactory)
```

**功能描述**：取每个窗口的**第一个**元素（throttleFirst 语义）：发射一个元素后，在 timespan 内跳过后续元素。

**返回值**：采样首个值的 `Flux`。

---

### 3.25 sampleTimeout

**方法签名**

```java
public final <U> Flux<T> sampleTimeout(Function<? super T, ? extends Publisher<U>> throttlerFactory)
public final <U> Flux<T> sampleTimeout(Function<? super T, ? extends Publisher<U>> throttlerFactory, int maxConcurrency)
```

**功能描述**：基于超时的去抖：对每个元素启动一个 throttler Publisher，若在 throttler 发射前没有新元素则输出该元素；若有新元素则丢弃旧值。最后一个元素总是输出（debounce 语义）。

**返回值**：去抖后的 `Flux`。

---

### 3.26 ofType

**方法签名**

```java
public final <U> Flux<U> ofType(final Class<U> clazz)
```

**功能描述**：只保留是指定类型的元素，并转换为该类型。等价于 `filter(clazz::isInstance).cast(clazz)`。

**返回值**：类型过滤后的 `Flux`。

```java
Flux.just(1, "a", 2).ofType(Integer.class).subscribe(System.out::println); // 1 2
```

---

## 四、组合操作符

### 4.1 startWith

**方法签名**

```java
public final Flux<T> startWith(Iterable<? extends T> iterable)
public final Flux<T> startWith(T... values)
public final Flux<T> startWith(Publisher<? extends T> publisher)
```

**功能描述**：在当前 `Flux` 之前先发射指定的值/Publisher，然后接当前流。

**返回值**：前置元素的 `Flux`。

```java
Flux.just(3, 4).startWith(1, 2).subscribe(System.out::println); // 1 2 3 4
```

---

### 4.2 concatWith

**方法签名**

```java
public final Flux<T> concatWith(Publisher<? extends T> other)
```

**功能描述**：在当前 `Flux` 完成后，串联发射 `other`。

**返回值**：串联的 `Flux`。

---

### 4.3 concatWithValues

**方法签名**

```java
public final Flux<T> concatWithValues(T... values)
```

**功能描述**：在当前 `Flux` 完成后，串联发射指定值。

**返回值**：串联值的 `Flux`。

---

### 4.4 mergeWith

**方法签名**

```java
public final Flux<T> mergeWith(Publisher<? extends T> other)
```

**功能描述**：将当前 `Flux` 与 `other` 并发合并，按到达顺序交错输出。

**返回值**：合并的 `Flux`。

---

### 4.5 mergeOrderedWith

**方法签名**

```java
public final Flux<T> mergeOrderedWith(Publisher<? extends T> other, Comparator<? super T> otherComparator)
```

**功能描述**：将当前 `Flux` 与 `other` 按整体有序合并（要求两者已排序）。

**返回值**：有序合并的 `Flux`。

---

### 4.6 mergeComparingWith

**方法签名**

```java
public final Flux<T> mergeComparingWith(Publisher<? extends T> other, Comparator<? super T> otherComparator)
```

**功能描述**：将当前 `Flux` 与 `other` 按比较器优先级合并（与 `mergeOrderedWith` 语义相近，基于比较器选择当前较小者输出）。

**返回值**：按比较器合并的 `Flux`。

---

### 4.7 zipWith

**方法签名**

```java
public final <T2> Flux<Tuple2<T, T2>> zipWith(Publisher<? extends T2> source2)
public final <T2, V> Flux<V> zipWith(Publisher<? extends T2> source2, BiFunction<? super T, ? super T2, ? extends V> combinator)
public final <T2, V> Flux<V> zipWith(Publisher<? extends T2> source2, int prefetch, BiFunction<? super T, ? super T2, ? extends V> combinator)
public final <T2> Flux<Tuple2<T, T2>> zipWith(Publisher<? extends T2> source2, int prefetch)
```

**功能描述**：将当前 `Flux` 与 `source2` 进行 zip，逐对组合。

**返回值**：zip 后的 `Flux`。

---

### 4.8 zipWithIterable

> 任务列表中的 `zipIterableWith` 在 3.7.19 中实际方法名为 `zipWithIterable`。

**方法签名**

```java
public final <T2> Flux<Tuple2<T, T2>> zipWithIterable(Iterable<? extends T2> iterable)
public final <T2, V> Flux<V> zipWithIterable(Iterable<? extends T2> iterable, BiFunction<? super T, ? super T2, ? extends V> zipper)
```

**功能描述**：将当前 `Flux` 与一个 `Iterable` 逐元素 zip 配对。

**返回值**：与 Iterable 配对的 `Flux`。

---

### 4.9 combineLatestWith（说明）

> 任务列表中的 `combineLatestWith` 在 reactor-core 3.7.19 的 `Flux.java` 中**未提供独立的实例方法**。如需对当前 `Flux` 与另一个源做 combineLatest，应使用静态方法 `Flux.combineLatest(this, other, combinator)`。

---

### 4.10 withLatestFrom

**方法签名**

```java
public final <U, R> Flux<R> withLatestFrom(Publisher<? extends U> other, BiFunction<? super T, ? super U, ? extends R> resultSelector)
```

**功能描述**：当当前 `Flux` 发射元素时，与 `other` 的**最新**值组合输出；`other` 未发射过任何值时，当前元素被丢弃。是单向的（仅当前流触发），区别于 `combineLatest`（双向触发）。

**返回值**：与 other 最新值组合的 `Flux`。

---

### 4.11 join

**方法签名**

```java
public final <TRight, TLeftEnd, TRightEnd, R> Flux<R> join(Publisher<? extends TRight> other,
        Function<? super T, ? extends Publisher<TLeftEnd>> leftEnd,
        Function<? super TRight, ? extends Publisher<TRightEnd>> rightEnd,
        BiFunction<? super T, ? super TRight, ? extends R> resultSelector)
```

**功能描述**：基于时间窗口的 join：当左元素和右元素的时间窗口重叠时，组合两者输出。窗口由 leftEnd/rightEnd 返回的 Publisher 控制。

**返回值**：窗口重叠时组合的 `Flux`。

---

### 4.12 groupJoin

**方法签名**

```java
public final <TRight, TLeftEnd, TRightEnd, R> Flux<R> groupJoin(Publisher<? extends TRight> other,
        Function<? super T, ? extends Publisher<TLeftEnd>> leftEnd,
        Function<? super TRight, ? extends Publisher<TRightEnd>> rightEnd,
        BiFunction<? super T, Flux<TRight>, ? extends R> resultSelector)
```

**功能描述**：与 `join` 类似，但组合时第二个参数是 `Flux<TRight>`（与左元素窗口重叠的所有右元素流），而非单个值。

**返回值**：分组 join 的 `Flux`。

---

### 4.13 or

**方法签名**

```java
public final Flux<T> or(Publisher<? extends T> other)
```

**功能描述**：与 `firstWithSignal` 语义一致：当前 `Flux` 与 `other` 竞速，只转发第一个发出信号的源。

**返回值**：最快源的 `Flux`。

---

### 4.14 then

**方法签名**

```java
public final Mono<Void> then()
```

**功能描述**：忽略当前 `Flux` 的所有元素，只等待其完成，然后转发完成信号到 `Mono<Void>`。

**返回值**：仅完成信号的 `Mono`。

---

### 4.15 thenEmpty

**方法签名**

```java
public final Mono<Void> thenEmpty(Publisher<Void> other)
```

**功能描述**：当前 `Flux` 完成后，订阅 `other`（`Publisher<Void>`），只转发其完成信号。

**返回值**：串联完成的 `Mono`。

---

### 4.16 thenMany

**方法签名**

```java
public final <V> Flux<V> thenMany(Publisher<V> other)
```

**功能描述**：当前 `Flux` 完成后，订阅 `other` 并转发其元素。忽略当前流元素。

**返回值**：当前流完成后转发 other 的 `Flux`。

```java
Flux.just(1, 2).thenMany(Flux.just("a", "b")).subscribe(System.out::println); // a b
```

> 注：任务列表中的 `then(Mono)` 实例方法在 3.7.19 中也存在（`public final <V> Mono<V> then(Mono<V> other)`），当前流完成后转发 `other` Mono 的结果。

---

## 五、错误处理操作符

### 5.1 onErrorReturn

**方法签名**

```java
public final Flux<T> onErrorReturn(T fallbackValue)
public final <E extends Throwable> Flux<T> onErrorReturn(Class<E> type, T fallbackValue)
public final Flux<T> onErrorReturn(Predicate<? super Throwable> predicate, T fallbackValue)
```

**功能描述**：发生错误时发射一个回退值后完成。可按异常类型/谓词限定匹配的错误。

**返回值**：错误时回退值的 `Flux`。

```java
Flux.error(new RuntimeException()).onErrorReturn("fallback").subscribe(System.out::println); // fallback
```

---

### 5.2 onErrorResume

**方法签名**

```java
public final Flux<T> onErrorResume(Function<? super Throwable, ? extends Publisher<? extends T>> fallback)
public final <E extends Throwable> Flux<T> onErrorResume(Class<E> type, Function<? super E, ? extends Publisher<? extends T>> fallback)
public final Flux<T> onErrorResume(Predicate<? super Throwable> predicate, Function<? super Throwable, ? extends Publisher<? extends T>> fallback)
```

**功能描述**：发生错误时切换到 fallback 函数返回的 `Publisher`（可动态决策）。比 `onErrorReturn` 更灵活（可发射多个/异步）。

**返回值**：错误时切换备选源的 `Flux`。

```java
Flux.error(new RuntimeException()).onErrorResume(e -> Flux.just("a", "b")).subscribe(System.out::println); // a b
```

---

### 5.3 onErrorMap

**方法签名**

```java
public final Flux<T> onErrorMap(Function<? super Throwable, ? extends Throwable> mapper)
public final <E extends Throwable> Flux<T> onErrorMap(Class<E> type, Function<? super E, ? extends Throwable> mapper)
public final Flux<T> onErrorMap(Predicate<? super Throwable> predicate, Function<? super Throwable, ? extends Throwable> mapper)
```

**功能描述**：将错误转换为另一个错误（同步映射），不恢复，只转换异常类型。

**返回值**：转换错误类型的 `Flux`。

---

### 5.4 onErrorComplete

**方法签名**

```java
public final Flux<T> onErrorComplete()
public final Flux<T> onErrorComplete(Class<? extends Throwable> type)
public final Flux<T> onErrorComplete(Predicate<? super Throwable> predicate)
```

**功能描述**：发生错误时直接转为 onComplete（吞掉错误）。可按类型/谓词限定。

**返回值**：错误时完成的 `Flux`。

---

### 5.5 onErrorContinue

**方法签名**

```java
public final Flux<T> onErrorContinue(BiConsumer<Throwable, Object> errorConsumer)
public final <E extends Throwable> Flux<T> onErrorContinue(Class<E> type, BiConsumer<Throwable, Object> errorConsumer)
public final <E extends Throwable> Flux<T> onErrorContinue(Predicate<E> errorPredicate, BiConsumer<Throwable, Object> errorConsumer)
```

**功能描述**：让**上游兼容操作符**在处理某个元素出错时丢弃该元素并继续处理后续元素（而非终止）。通过 context 传播策略实现，作用域是上游。属于专家级操作符，官方建议优先在具体内层流用 `onErrorResume` 替代。

**参数说明**
- `errorConsumer`：接收错误与触发值的回调。
- `type`/`errorPredicate`：限定可恢复的错误。

**返回值**：上游可继续的 `Flux`。

---

### 5.6 onErrorStop

**方法签名**

```java
public final Flux<T> onErrorStop()
```

**功能描述**：恢复默认的"错误即终止"语义，用于显式终止下游 `onErrorContinue` 的影响范围（避免向上游泄漏）。

**返回值**：错误即终止的 `Flux`。

---

### 5.7 retry

**方法签名**

```java
public final Flux<T> retry()
public final Flux<T> retry(long numRetries)
```

**功能描述**：发生错误时重新订阅上游。无参版本无限重试；带参版本限制重试次数 `numRetries`（总订阅 = numRetries + 1）。

**返回值**：出错重试的 `Flux`。

```java
Flux.error(new RuntimeException()).retry(3).subscribe(System.out::println, e -> System.err.println(e));
```

---

### 5.8 retryWhen

**方法签名**

```java
public final Flux<T> retryWhen(Retry retrySpec)
```

**功能描述**：基于 `Retry` 策略的重试，可定制退避（如 `Retry.backoff`）、最大次数、过滤等。companion `Flux<RetrySignal>` 由 retrySpec 处理。

**返回值**：按策略重试的 `Flux`。

```java
flux.retryWhen(Retry.backoff(3, Duration.ofSeconds(1))).subscribe();
```

---

### 5.9 doOnError

**方法签名**

```java
public final Flux<T> doOnError(Consumer<? super Throwable> onError)
public final <E extends Throwable> Flux<T> doOnError(Class<E> exceptionType, Consumer<? super E> onError)
public final Flux<T> doOnError(Predicate<? super Throwable> predicate, Consumer<? super Throwable> onError)
```

**功能描述**：当错误发生时执行副作用（如记日志），不修改错误，错误仍向下传递。可按类型/谓词限定。

**返回值**：附加错误副作用的 `Flux`。

---

## 六、副作用操作符

### 6.1 doOnNext

**方法签名**

```java
public final Flux<T> doOnNext(Consumer<? super T> onNext)
```

**功能描述**：每个元素发射前执行副作用。

```java
Flux.just(1, 2).doOnNext(System.out::println).subscribe();
```

---

### 6.2 doOnEach

**方法签名**

```java
public final Flux<T> doOnEach(Consumer<? super Signal<T>> signalConsumer)
```

**功能描述**：对每个信号（onNext/onError/onComplete）执行副作用，通过 `Signal<T>` 获取信号类型与值。

**返回值**：附加每信号副作用的 `Flux`。

---

### 6.3 doOnSubscribe

**方法签名**

```java
public final Flux<T> doOnSubscribe(Consumer<? super Subscription> onSubscribe)
```

**功能描述**：订阅时执行副作用，接收 `Subscription`。

---

### 6.4 doOnRequest

**方法签名**

```java
public final Flux<T> doOnRequest(LongConsumer consumer)
```

**功能描述**：下游向上游 request 时执行副作用，接收请求量。

---

### 6.5 doOnComplete

**方法签名**

```java
public final Flux<T> doOnComplete(Runnable onComplete)
```

**功能描述**：源正常完成时执行副作用。

---

### 6.6 doOnCancel

**方法签名**

```java
public final Flux<T> doOnCancel(Runnable onCancel)
```

**功能描述**：被下游取消时执行副作用。

---

### 6.7 doAfterTerminate

**方法签名**

```java
public final Flux<T> doAfterTerminate(Runnable afterTerminate)
```

**功能描述**：在 onComplete/onError 信号**传递给下游之后**执行副作用。

---

### 6.8 doOnTerminate

**方法签名**

```java
public final Flux<T> doOnTerminate(Runnable onTerminate)
```

**功能描述**：在 onComplete/onError 信号**传递给下游之前**执行副作用（无论成功或失败）。

---

### 6.9 doFirst

**方法签名**

```java
public final Flux<T> doFirst(Runnable onFirst)
```

**功能描述**：在订阅链**最开始**（先于上游 doOnSubscribe）执行副作用。多次调用按反向顺序执行。

---

### 6.10 doFinally

**方法签名**

```java
public final Flux<T> doFinally(Consumer<SignalType> onFinally)
```

**功能描述**：无论以何种方式终止（完成/错误/取消）都执行一次副作用，接收 `SignalType` 表明终止类型。是资源清理的推荐方式。

**返回值**：附加终止副作用的 `Flux`。

---

### 6.11 doOnDiscard

**方法签名**

```java
public final <R> Flux<T> doOnDiscard(final Class<R> type, final Consumer<? super R> discardHook)
```

**功能描述**：当元素被内部缓冲并随后被丢弃（取消/错误/溢出等）时，对匹配类型的元素执行清理副作用。用于释放资源型元素。

---

### 6.12 tap

**方法签名**

```java
public final Flux<T> tap(Supplier<SignalListener<T>> simpleListenerGenerator)
public final Flux<T> tap(Function<ContextView, SignalListener<T>> listenerGenerator)
public final Flux<T> tap(SignalListenerFactory<T, ?> listenerFactory)
```

**功能描述**：通过 `SignalListener`/`SignalListenerFactory` 全生命周期钩子观察流（订阅/请求/onNext/ onComplete/onError/取消等），是 `doOnXxx` 系列的统一可扩展替代。不会修改流。

**返回值**：附加监听器的 `Flux`。

---

## 七、时间操作符

### 7.1 elapsed

**方法签名**

```java
public final Flux<Tuple2<Long, T>> elapsed()
public final Flux<Tuple2<Long, T>> elapsed(Scheduler scheduler)
```

**功能描述**：将每个元素与距上一元素的时间间隔（毫秒）配对为 `Tuple2<Long, T>`。

**返回值**：带时间间隔的 `Flux`。

```java
Flux.interval(Duration.ofMillis(100)).take(2).elapsed().subscribe(System.out::println);
```

---

### 7.2 timestamp

**方法签名**

```java
public final Flux<Tuple2<Long, T>> timestamp()
public final Flux<Tuple2<Long, T>> timestamp(Scheduler scheduler)
```

**功能描述**：将每个元素与当前时间戳（毫秒，来自调度器）配对为 `Tuple2<Long, T>`。

**返回值**：带时间戳的 `Flux`。

---

### 7.3 timed

**方法签名**

```java
public final Flux<Timed<T>> timed()
public final Flux<Timed<T>> timed(Scheduler clock)
```

**功能描述**：将元素包装为 `Timed<T>`，同时提供时间戳、距上一元素间隔、自订阅以来总时长三重信息。是 `elapsed`+`timestamp` 的增强版。

**返回值**：发射 `Timed<T>` 的 `Flux`。

---

### 7.4 delayElements

**方法签名**

```java
public final Flux<T> delayElements(Duration delay)
public final Flux<T> delayElements(Duration delay, Scheduler timer)
```

**功能描述**：对每个元素延迟指定时长后再发射（每元素独立延迟）。

**返回值**：每元素延迟的 `Flux`。

```java
Flux.just(1, 2).delayElements(Duration.ofMillis(100)).subscribe(System.out::println);
```

---

### 7.5 delaySequence

**方法签名**

```java
public final Flux<T> delaySequence(Duration delay)
public final Flux<T> delaySequence(Duration delay, Scheduler timer)
```

**功能描述**：对整个序列延迟：将每个信号（包括 onComplete）整体后移指定时长，但**不改变元素间隔**（保持序列内部时序）。与 `delayElements` 区别：后者逐元素延迟会拉大间隔。

**返回值**：整体延迟的 `Flux`。

---

### 7.6 delaySubscription

**方法签名**

```java
public final Flux<T> delaySubscription(Duration delay)
public final Flux<T> delaySubscription(Duration delay, Scheduler timer)
public final <U> Flux<T> delaySubscription(Publisher<U> subscriptionDelay)
```

**功能描述**：延迟订阅上游：在订阅后等待指定时长（或 `subscriptionDelay` 发射信号）才真正订阅源。

**返回值**：延迟订阅的 `Flux`。

---

### 7.7 delayUntil

**方法签名**

```java
public final Flux<T> delayUntil(Function<? super T, ? extends Publisher<?>> triggerProvider)
```

**功能描述**：对每个元素，先发射触发 `Publisher`，等其完成后才转发该元素。可用于"等依赖条件满足再放行"。

**返回值**：按触发器放行的 `Flux`。

---

### 7.8 debounce（说明）

> reactor-core 3.7.19 的 `Flux.java` 中**没有名为 `debounce` 的方法**。Reactor 通过 `sample(Duration)`（throttleLast 语义）或 `sampleTimeout`（去抖语义）实现等价能力。如需"元素在静默 N 时间后才发出"的经典 debounce 语义，可用 `sample(Duration)` 或 `sampleTimeout` 组合实现。

---

### 7.9 throttleFirst（说明）

> reactor-core 3.7.19 中**没有名为 `throttleFirst` 的方法**，等价能力由 `sampleFirst(Duration)` 提供：取每个时间窗口的第一个元素。

---

### 7.10 throttleLast（说明）

> reactor-core 3.7.19 中**没有名为 `throttleLast` 的方法**，等价能力由 `sample(Duration)` 提供：取每个时间窗口的最新元素。

---

### 7.11 cache

**方法签名**

```java
public final Flux<T> cache()
public final Flux<T> cache(int history)
public final Flux<T> cache(Duration ttl)
public final Flux<T> cache(Duration ttl, Scheduler timer)
public final Flux<T> cache(int history, Duration ttl)
public final Flux<T> cache(int history, Duration ttl, Scheduler timer)
```

**功能描述**：将源转为热源，订阅后缓存发射的元素给后续订阅者。`history` 限制缓存数量；`ttl` 限制单元素存活时长，过期后重新订阅源。底层是 `replay(...).autoConnect()`。

**返回值**：缓存的 `Flux`。

```java
Flux<Integer> cached = Flux.range(1, 5).cache();
cached.subscribe(System.out::println);
cached.subscribe(System.out::println); // 第二次直接走缓存
```

---

### 7.12 cacheInvalidateIf（说明）

> reactor-core 3.7.19 的 `Flux.java` 中**未提供 `cacheInvalidateIf` 方法**（该方法在更高版本或不同分支中存在）。当前版本可用的缓存控制手段为 `cache(int history)`、`cache(Duration ttl)` 及其组合重载，通过 history/TTL 控制缓存失效。

---

### 7.13 replay

**方法签名**

```java
public final ConnectableFlux<T> replay()
public final ConnectableFlux<T> replay(int history)
public final ConnectableFlux<T> replay(Duration ttl)
public final ConnectableFlux<T> replay(int history, Duration ttl)
public final ConnectableFlux<T> replay(Duration ttl, Scheduler timer)
public final ConnectableFlux<T> replay(int history, Duration ttl, Scheduler timer)
```

**功能描述**：转为 `ConnectableFlux`（可热多播），缓存元素供后续订阅者重放。需要手动 `connect()` 才开始订阅上游。`history`/`ttl` 控制缓存范围。

**返回值**：可重放的 `ConnectableFlux`。

---

### 7.14 timeout

**方法签名**

```java
public final Flux<T> timeout(Duration timeout)
public final Flux<T> timeout(Duration timeout, @Nullable Publisher<? extends T> fallback)
public final Flux<T> timeout(Duration timeout, Scheduler timer)
public final Flux<T> timeout(Duration timeout, @Nullable Publisher<? extends T> fallback, Scheduler timer)
public final <U> Flux<T> timeout(Publisher<U> firstTimeout)
public final <U, V> Flux<T> timeout(Publisher<U> firstTimeout, Function<? super T, ? extends Publisher<V>> nextTimeoutFactory)
public final <U, V> Flux<T> timeout(Publisher<U> firstTimeout, Function<? super T, ? extends Publisher<V>> nextTimeoutFactory, @Nullable Publisher<? extends T> fallback)
```

**功能描述**：超时控制。若首个元素或相邻元素间隔超过指定时长/Publisher 未发信号，则抛 `TimeoutException`；提供 fallback 时切换到 fallback。

**返回值**：超时控制/回退的 `Flux`。

```java
Flux.never().timeout(Duration.ofMillis(100)).subscribe(System.out::println, e -> System.err.println(e));
```

---

## 八、聚合/规约操作符

### 8.1 reduce

**方法签名**

```java
public final Mono<T> reduce(BiFunction<T, T, T> aggregator)
public final <A> Mono<A> reduce(A initial, BiFunction<A, ? super T, A> accumulator)
```

**功能描述**：将所有元素规约为单个值（`Mono`），只发射最终结果（区别于 `scan` 发射中间值）。无初始值版本以第一个元素为种子。

**返回值**：规约结果的 `Mono`。

```java
Flux.just(1, 2, 3).reduce(0, (a, b) -> a + b).subscribe(System.out::println); // 6
```

---

### 8.2 reduceWith

**方法签名**

```java
public final <A> Mono<A> reduceWith(Supplier<A> initial, BiFunction<A, ? super T, A> accumulator)
```

**功能描述**：与 `reduce(initial, ...)` 类似，但初始值通过 `Supplier` 每次订阅延迟提供，避免共享。

**返回值**：规约结果的 `Mono`。

---

### 8.3 collect

**方法签名**

```java
public final <E> Mono<E> collect(Supplier<E> containerSupplier, BiConsumer<E, ? super T> collector)
public final <R, A> Mono<R> collect(Collector<? super T, A, ? extends R> collector)
```

**功能描述**：将元素收集到容器中，源完成后发射容器。支持自定义容器+收集器，或直接用 `java.util.stream.Collector`。

**返回值**：收集结果的 `Mono`。

```java
Flux.just(1, 2, 3).collect(Collectors.toList()).subscribe(System.out::println); // [1, 2, 3]
```

---

### 8.4 collectList

**方法签名**

```java
public final Mono<List<T>> collectList()
```

**功能描述**：将所有元素收集到 `List`，源完成后发射该 List。

**返回值**：包含 List 的 `Mono`。

---

### 8.5 collectSortedList

**方法签名**

```java
public final Mono<List<T>> collectSortedList()
public final Mono<List<T>> collectSortedList(@Nullable Comparator<? super T> comparator)
```

**功能描述**：将元素收集到 List 并排序（默认自然序，或指定比较器）后发射。

**返回值**：已排序 List 的 `Mono`。

---

### 8.6 collectMap

**方法签名**

```java
public final <K> Mono<Map<K, T>> collectMap(Function<? super T, ? extends K> keyExtractor)
public final <K, V> Mono<Map<K, V>> collectMap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends V> valueExtractor)
public final <K, V> Mono<Map<K, V>> collectMap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends V> valueExtractor, Supplier<Map<K, V>> mapSupplier)
```

**功能描述**：将元素按 key 收集到 `Map`，相同 key 后者覆盖前者。

**返回值**：包含 Map 的 `Mono`。

---

### 8.7 collectMultimap

**方法签名**

```java
public final <K> Mono<Map<K, Collection<T>>> collectMultimap(Function<? super T, ? extends K> keyExtractor)
public final <K, V> Mono<Map<K, Collection<V>>> collectMultimap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends V> valueExtractor)
public final <K, V> Mono<Map<K, Collection<V>>> collectMultimap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends V> valueExtractor, Supplier<Map<K, Collection<V>>> mapSupplier)
```

**功能描述**：将元素按 key 收集到 `Map<K, Collection<V>>`，相同 key 追加到集合（一对多）。

**返回值**：包含 Multimap 的 `Mono`。

---

### 8.8 count

**方法签名**

```java
public final Mono<Long> count()
```

**功能描述**：统计元素个数，源完成后发射 Long。

**返回值**：元素数的 `Mono`。

---

### 8.9 all

**方法签名**

```java
public final Mono<Boolean> all(Predicate<? super T> predicate)
```

**功能描述**：判断是否所有元素都满足谓词（遇第一个 false 立即发射 false 并取消上游）；全部满足则发射 true。

**返回值**：布尔结果的 `Mono`。

---

### 8.10 any

**方法签名**

```java
public final Mono<Boolean> any(Predicate<? super T> predicate)
```

**功能描述**：判断是否存在元素满足谓词（遇第一个 true 立即发射 true 并取消上游）；都不满足则 false。

**返回值**：布尔结果的 `Mono`。

---

### 8.11 hasElement

**方法签名**

```java
public final Mono<Boolean> hasElement(T value)
```

**功能描述**：判断源是否发射过指定值（用 equals 比较），找到立即发射 true 并取消上游。

**返回值**：布尔结果的 `Mono`。

> 注：判断流是否非空（任意元素）使用 `hasElements()`。

---

### 8.12 hasElements

**方法签名**

```java
public final Mono<Boolean> hasElements()
```

**功能描述**：判断源是否发射过任意元素（至少一个），发射过即 true 并取消上游。

**返回值**：布尔结果的 `Mono`。

---

## 九、背压操作符

### 9.1 onBackpressureBuffer

**方法签名**

```java
public final Flux<T> onBackpressureBuffer()
public final Flux<T> onBackpressureBuffer(int maxSize)
public final Flux<T> onBackpressureBuffer(int maxSize, Consumer<? super T> onOverflow)
public final Flux<T> onBackpressureBuffer(int maxSize, BufferOverflowStrategy bufferOverflowStrategy)
public final Flux<T> onBackpressureBuffer(int maxSize, Consumer<? super T> onBufferOverflow, BufferOverflowStrategy bufferOverflowStrategy)
public final Flux<T> onBackpressureBuffer(Duration ttl, int maxSize, Consumer<? super T> onBufferEviction)
public final Flux<T> onBackpressureBuffer(Duration ttl, int maxSize, Consumer<? super T> onBufferEviction, Scheduler scheduler)
```

**功能描述**：当下游需求不足时，向上游无限请求并缓冲元素。可限制缓冲大小，超限时按 `BufferOverflowStrategy`（ERROR/DROP_OLDEST/DROP_LATEST）处理；可设 TTL 过期清理。

**返回值**：缓冲背压的 `Flux`。

---

### 9.2 onBackpressureDrop

**方法签名**

```java
public final Flux<T> onBackpressureDrop()
public final Flux<T> onBackpressureDrop(Consumer<? super T> onDropped)
```

**功能描述**：当下游需求不足时，丢弃溢出的元素（可选通知丢弃回调）。

**返回值**：丢弃背压的 `Flux`。

---

### 9.3 onBackpressureError

**方法签名**

```java
public final Flux<T> onBackpressureError()
```

**功能描述**：当下游需求不足时，直接抛 `OverflowException` 终止。

**返回值**：错误背压的 `Flux`。

---

### 9.4 onBackpressureLatest

**方法签名**

```java
public final Flux<T> onBackpressureLatest()
```

**功能描述**：当下游需求不足时，只保留最新一个元素，丢弃之前的（类似 latest 缓存）。

**返回值**：保留最新值的背压 `Flux`。

---

### 9.5 limitRate / limitRequest（背压视角）

参见 §3.15 / §3.16。`limitRate` 控制批量请求策略，`limitRequest` 控制总请求量上限，均为背压调节手段。

---

## 十、日志/调试操作符

### 10.1 log

**方法签名**

```java
public final Flux<T> log()
public final Flux<T> log(String category)
public final Flux<T> log(@Nullable String category, Level level, SignalType... options)
public final Flux<T> log(@Nullable String category, Level level, boolean showErrorLine, SignalType... options)
public final Flux<T> log(Logger logger)
public final Flux<T> log(Logger logger, Level level, boolean showErrorLine, SignalType... options)
```

**功能描述**：以 `java.util.logging`（或自定义 `Logger`）记录所有信号（onNext/onComplete/onError/request/subscribe/cancel）。可指定 category、级别、要记录的信号类型。

**返回值**：附加日志的 `Flux`。

```java
Flux.just(1, 2).log("my.category").subscribe();
```

---

### 10.2 checkpoint

**方法签名**

```java
public final Flux<T> checkpoint()
public final Flux<T> checkpoint(String description)
public final Flux<T> checkpoint(@Nullable String description, boolean forceStackTrace)
```

**功能描述**：在装配点打标记，错误发生时在堆栈中显示装配 traceback，便于定位。无参版本生成完整堆栈（开销大）；带 description 仅显示描述；`forceStackTrace` 控制是否强制堆栈。

**返回值**：带检查点的 `Flux`。

---

### 10.3 tap（调试视角）

参见 §6.12。`tap` 通过 `SignalListener` 提供全生命周期观察，可用于精细调试与指标采集。

---

### 10.4 metrics

**方法签名**

```java
public final Flux<T> metrics()
```

**功能描述**：当 Micrometer 可用时，注册流相关指标（订阅数、请求数、元素数等），便于监控。

**返回值**：附加指标的 `Flux`。

---

### 10.5 name

**方法签名**

```java
public final Flux<T> name(String name)
```

**功能描述**：为该链路命名，名称会进入 `Context`，可被 `metrics`/checkpoint 等使用，便于在监控/堆栈中识别。

**返回值**：命名的 `Flux`。

---

### 10.6 tag

**方法签名**

```java
public final Flux<T> tag(String key, String value)
```

**功能描述**：为该链路附加键值标签（可多次调用），与 `name` 一样进入 `Context`，供监控/追踪使用。

**返回值**：带标签的 `Flux`。

---

## 十一、调度与生命周期

### 11.1 publishOn

**方法签名**

```java
public final Flux<T> publishOn(Scheduler scheduler)
public final Flux<T> publishOn(Scheduler scheduler, int prefetch)
public final Flux<T> publishOn(Scheduler scheduler, boolean delayError, int prefetch)
```

**功能描述**：切换**下游**执行线程：让后续操作符在指定 `Scheduler` 的 Worker 上执行 onNext/onComplete/onError。影响其下游的线程上下文。`prefetch` 控制异步队列容量；`delayError` 控制是否先消费完队列再传错误。

**返回值**：切换调度器的 `Flux`。

```java
Flux.just(1, 2).publishOn(Schedulers.parallel()).map(i -> i * 2).subscribe();
```

---

### 11.2 subscribeOn

**方法签名**

```java
public final Flux<T> subscribeOn(Scheduler scheduler)
public final Flux<T> subscribeOn(Scheduler scheduler, boolean requestOnSeparateThread)
```

**功能描述**：切换**上游订阅**线程：让源（及上游操作符）在指定 `Scheduler` 上被订阅。影响整个链路最上游的执行线程。`requestOnSeparateThread` 控制 request 是否单独线程。

**返回值**：切换订阅线程的 `Flux`。

---

### 11.3 publish

**方法签名**

```java
public final ConnectableFlux<T> publish()
public final ConnectableFlux<T> publish(int prefetch)
public final <R> Flux<R> publish(Function<? super Flux<T>, ? extends Publisher<? extends R>> transform)
public final <R> Flux<R> publish(Function<? super Flux<T>, ? extends Publisher<? extends R>> transform, int prefetch)
```

**功能描述**：转为 `ConnectableFlux`（热多播），多个订阅者共享一次上游订阅。`publish(transform)` 版本允许在共享的流上应用转换函数，实现"共享上游 + 各自处理"。需要 `connect()` 或 `autoConnect(n)`。

**返回值**：可连接/共享的 `Flux` 或 `ConnectableFlux`。

---

### 11.4 share

**方法签名**

```java
public final Flux<T> share()
```

**功能描述**：将源变为共享热源：第一个订阅者触发订阅，后续订阅者共享同一上游；当最后一个订阅者取消时取消上游；新订阅者重新订阅。等价于 `publish().refCount()`。

**返回值**：共享的 `Flux`。

---

### 11.5 shareNext

**方法签名**

```java
public final Mono<T> shareNext()
```

**功能描述**：将源的第一个元素作为热 `Mono` 共享：第一个订阅者触发上游订阅，第一个元素被缓存并广播给所有当前/后续订阅者；之后上游被取消。

**返回值**：共享首元素的 `Mono`。

---

### 11.6 parallel

**方法签名**

```java
public final ParallelFlux<T> parallel()
public final ParallelFlux<T> parallel(int parallelism)
public final ParallelFlux<T> parallel(int parallelism, int prefetch)
```

**功能描述**：将数据按轮询（round-robin）分发到 `parallelism` 个"轨道"，返回 `ParallelFlux`。需配合 `runOn(Scheduler)` 才真正并行执行。常用于并行处理 CPU 密集型任务。

**返回值**：并行 `ParallelFlux`。

```java
Flux.range(1, 100).parallel(4).runOn(Schedulers.parallel()).map(i -> i * 2).sequential().subscribe();
```

---

### 11.7 cancelOn

**方法签名**

```java
public final Flux<T> cancelOn(Scheduler scheduler)
```

**功能描述**：使下游的取消信号在指定 `Scheduler` 上执行，避免取消操作阻塞当前线程。

**返回值**：调度取消的 `Flux`。

---

## 十二、上下文操作

### 12.1 contextWrite

**方法签名**

```java
public final Flux<T> contextWrite(ContextView contextToAppend)
public final Flux<T> contextWrite(Function<Context, Context> contextModifier)
```

**功能描述**：向 `Context` 追加键值对，供上游操作符读取。`ContextView` 版本直接追加；`Function` 版本基于当前 Context 计算新 Context。注意：contextWrite 是从下游向上游传播，需放在读取上下文操作符的下游。

**返回值**：附加上下文的 `Flux`。

```java
Flux.deferContextual(ctx -> Flux.just(ctx.getOrDefault("k", "default")))
    .contextWrite(Context.of("k", "v"))
    .subscribe(System.out::println); // v
```

---

### 12.2 contextCapture

**方法签名**

```java
public final Flux<T> contextCapture()
```

**功能描述**：显式声明在此处捕获上游 `Context`，使后续能以 `ContextView` 形式访问。用于在特定点固化上下文快照。

**返回值**：捕获上下文的 `Flux`。

---

### 12.3 deferContextual（上下文视角）

参见 §1.14。`deferContextual` 是消费 `ContextView` 创建源的标准入口，与 `contextWrite` 配合构成上下文传播闭环。

---

## 十三、订阅与阻塞操作

### 13.1 subscribe

**方法签名**

```java
public final Disposable subscribe()
public final Disposable subscribe(Consumer<? super T> consumer)
public final Disposable subscribe(@Nullable Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer)
public final Disposable subscribe(@Nullable Consumer<? super T> consumer, @Nullable Consumer<? super Throwable> errorConsumer, Runnable completeConsumer)
public final Disposable subscribe(@Nullable Consumer<? super T> consumer, @Nullable Consumer<? super Throwable> errorConsumer, @Nullable Runnable completeConsumer, @Nullable Context context)
public final Disposable subscribe(@Nullable Consumer<? super T> consumer, @Nullable Consumer<? super Throwable> errorConsumer, @Nullable Runnable completeConsumer, @Nullable Consumer<? super Subscription> subscriptionConsumer)
public final void subscribe(Subscriber<? super T> actual)
public abstract void subscribe(CoreSubscriber<? super T> actual)
```

**功能描述**：触发订阅并开始数据流动。提供多种便利重载：仅消费 onNext、加错误处理、加完成处理、加订阅处理、加 Context。返回 `Disposable` 用于取消。`Subscriber`/`CoreSubscriber` 版本用于自定义 Subscriber（`CoreSubscriber` 支持 Context 传递）。

**返回值**：便利版本返回 `Disposable`；Subscriber 版本返回 `void`。

```java
Flux.just(1, 2).subscribe(System.out::println, e -> {}, () -> System.out.println("done"));
```

---

### 13.2 subscribeWith

**方法签名**

```java
public final <E extends Subscriber<? super T>> E subscribeWith(E subscriber)
```

**功能描述**：用指定 `Subscriber` 订阅并返回该 Subscriber（便于链式获取结果）。

**返回值**：传入的 Subscriber。

---

### 13.3 blockFirst

**方法签名**

```java
public final T blockFirst()
public final T blockFirst(Duration timeout)
```

**功能描述**：阻塞当前线程订阅并等待第一个元素，返回后取消上游。超时抛 `IllegalStateException`。仅用于测试/桥接命令式代码。

**返回值**：第一个元素。

---

### 13.4 blockLast

**方法签名**

```java
public final T blockLast()
public final T blockLast(Duration timeout)
```

**功能描述**：阻塞当前线程订阅并等待最后一个元素（源完成后返回）。超时抛异常。

**返回值**：最后一个元素。

---

### 13.5 toIterable

**方法签名**

```java
public final Iterable<T> toIterable()
public final Iterable<T> toIterable(int batchSize)
public final Iterable<T> toIterable(int batchSize, @Nullable Supplier<Queue<T>> queueProvider)
```

**功能描述**：将 `Flux` 转为 `Iterable`（阻塞式桥接），迭代时通过队列与背压按 `batchSize` 拉取。

**返回值**：阻塞式 `Iterable`。

---

### 13.6 toStream

**方法签名**

```java
public final Stream<T> toStream()
public final Stream<T> toStream(int batchSize)
```

**功能描述**：将 `Flux` 转为 `java.util.stream.Stream`（阻塞式桥接），支持按 `batchSize` 背压拉取。

**返回值**：阻塞式 `Stream`。

---

### 13.7 toFuture（说明）

> reactor-core 3.7.19 的 `Flux.java` 中**没有 `toFuture` 方法**。`toFuture()` 是 `Mono` 的方法（返回 `CompletableFuture<T>`）。`Flux` 通常用 `collectList().toFuture()` 或 `blockLast()` 等桥接为未来/阻塞结果。

---

## 十四、其他方法

### 14.1 hide

**方法签名**

```java
public Flux<T> hide()
```

**功能描述**：隐藏当前 `Flux` 的具体实现类型（如 `ConnectableFlux`、`Fuseable` 标记等），强制后续操作符按通用 `Flux` 处理（禁用融合等优化）。用于调试或避免某些优化副作用。

**返回值**：隐藏类型的 `Flux`。

---

### 14.2 getPrefetch

**方法签名**

```java
public int getPrefetch()
```

**功能描述**：返回此 `Flux` 的预取量（如内部异步边界请求量）。默认实现返回 -1，由具体操作符覆盖。

**返回值**：预取量。

---

### 14.3 repeat

**方法签名**

```java
public final Flux<T> repeat()
public final Flux<T> repeat(BooleanSupplier predicate)
public final Flux<T> repeat(long numRepeat)
public final Flux<T> repeat(long numRepeat, BooleanSupplier predicate)
```

**功能描述**：源完成后重新订阅。无参无限重复；`predicate` 控制是否重复；`numRepeat` 限制重复次数（总订阅 = numRepeat + 1）。

**返回值**：重复的 `Flux`。

```java
Flux.just(1, 2).repeat(2).subscribe(System.out::println); // 1 2 1 2 1 2
```

---

### 14.4 repeatWhen

**方法签名**

```java
public final Flux<T> repeatWhen(Function<Flux<Long>, ? extends Publisher<?>> repeatFactory)
```

**功能描述**：基于 companion 流的响应式重复：源完成时向 companion 发射元素数（Long），companion 发射信号则重复，companion 终止则终止结果流。

**返回值**：响应式重复的 `Flux`。

---

### 14.5 sort

**方法签名**

```java
public final Flux<T> sort()
public final Flux<T> sort(Comparator<? super T> sortFunction)
```

**功能描述**：缓冲所有元素，源完成后按自然序（或指定比较器）排序后逐个发射。是 `collectSortedList().flatMapIterable` 的便利封装。

**返回值**：排序后的 `Flux`。

---

### 14.6 onTerminateDetach

**方法签名**

```java
public final Flux<T> onTerminateDetach()
```

**功能描述**：在终止或取消时解除上下游引用（断开 Subscriber 与 Subscription 的相互引用），帮助 GC 回收。用于与非 Reactor Subscriber 互操作时避免内存泄漏。

**返回值**：终止时解引用的 `Flux`。

---

## 十五、纵向关联分析

### 15.1 flatMap vs concatMap vs switchMap vs flatMapSequential

| 维度 | flatMap | concatMap | switchMap | flatMapSequential |
|------|---------|-----------|-----------|-------------------|
| 并发性 | 并发（默认 256） | 串行（一次一个） | 并发但只保留最新 | 并发（默认 256） |
| 顺序保证 | 无（按到达交错） | 严格保序 | 不保序（仅最新内层流） | 保序（按元素顺序输出） |
| 错误处理 | 立即终止 | 立即终止 | 立即终止 | 立即终止（DelayError 版延迟） |
| 内层流取消 | 不取消 | 不取消 | 新元素到达取消旧内层流 | 不取消 |
| 适用场景 | 高吞吐、不关心顺序 | 顺序敏感、强一致 | 取最新、搜索类 | 顺序敏感 + 高吞吐 |

**选择建议**：
- 需要**顺序**且**性能**：`flatMapSequential`
- 需要**顺序**且**简单**：`concatMap`
- 只关心**最新**结果：`switchMap`
- **高吞吐**、不关心顺序：`flatMap`

---

### 15.2 buffer vs window vs bufferTimeout

| 维度 | buffer | window | bufferTimeout |
|------|--------|--------|---------------|
| 输出类型 | `Flux<List<T>>`（集合） | `Flux<Flux<T>>`（嵌套流） | `Flux<List<T>>`（集合） |
| 切分依据 | 数量/时间/边界 Publisher | 数量/时间/边界 Publisher | 数量 **或** 时间（先到者） |
| 内存 | 一次性收集到集合 | 流式（低内存，适合大窗口） | 一次性收集，但有超时兜底 |
| 背压友好 | 集合大时压力小（元素少） | 窗口流需及时消费 | 较好 |
| 适用场景 | 批处理、聚合 | 流式分片处理、持续窗口 | 突发流量分批（避免等太久） |

**选择建议**：
- 窗口大、需流式处理：`window`
- 固定批量、简单：`buffer`
- 既限大小又限时间（防等待过久）：`bufferTimeout`

---

### 15.3 merge vs concat vs switchOnNext vs combineLatest vs zip

| 维度 | concat / concatWith | merge / mergeWith | switchOnNext | combineLatest | zip / zipWith |
|------|---------------------|-------------------|--------------|---------------|---------------|
| 订阅时机 | 顺序（前完才下个） | 并发 | 切换式（新源来取消旧） | 并发 | 并发 |
| 输出顺序 | 严格按源顺序 | 按到达交错 | 仅当前源 | 任一源更新触发 | 严格对齐（一一配对） |
| 元素来源 | 单源串联 | 多源合并 | 单源（动态切换） | 多源最新值组合 | 多源配对组合 |
| 完成条件 | 所有源完成 | 所有源完成 | 外层 + 当前源完成 | 所有源完成 | 任一源完成 |
| 典型场景 | 顺序拼接 | 并发聚合 | 取最新源 | 状态合成（如 UI 联动） | 配对（如坐标 x,y） |

**选择建议**：
- 要求**严格顺序拼接**：`concat`
- **并发合并、不关心顺序**：`merge`
- **动态切换到最新源**：`switchOnNext`
- **任一更新就组合最新值**：`combineLatest`
- **严格配对**：`zip`

---

### 15.4 take vs skip vs limitRate

| 维度 | take | skip | limitRate |
|------|------|------|-----------|
| 作用 | 取前 N 个（过滤下游可见量） | 跳过前 N 个（过滤下游可见量） | 控制向上游 request 量（背压） |
| 影响范围 | 下游可见元素 + 上游取消 | 下游可见元素 | 仅上游请求速率，元素全传 |
| 取消上游 | 取够后取消 | 不取消 | 不取消 |
| 时间维度 | 支持 `take(Duration)` | 支持 `skip(Duration)` | 不支持 |
| 典型场景 | 取头部/限流展示 | 跳过表头/预热 | 控压、避免上游过快 |

**补充**：`limitRequest(n)` 限制总请求量，发射满 N 后完成，介于 `take` 与 `limitRate` 之间。

---

### 15.5 onErrorContinue vs onErrorResume vs onErrorReturn vs onErrorMap vs onErrorComplete vs onErrorStop

| 维度 | onErrorContinue | onErrorResume | onErrorReturn | onErrorMap | onErrorComplete | onErrorStop |
|------|-----------------|---------------|---------------|------------|-----------------|-------------|
| 行为 | 丢弃出错元素继续 | 切换到备选 Publisher | 发射回退值后完成 | 转换错误类型 | 转为完成 | 恢复"错误即终止" |
| 是否恢复 | 是（继续后续） | 是（切换源） | 是（终止但发值） | 否（仍终止） | 是（吞错完成） | 否（终止） |
| 作用方向 | 上游兼容操作符 | 当前点 | 当前点 | 当前点 | 当前点 | 上游 |
| 元素丢失 | 丢弃触发元素 | 由 fallback 决定 | 由 fallback 决定 | 不丢 | 不丢 | 不丢 |
| 推荐度 | 专家级、谨慎用 | 推荐 | 推荐 | 推荐 | 视场景 | 用于隔离 OEC 范围 |

**选择建议**：
- **切换备选流**：`onErrorResume`
- **简单回退值**：`onErrorReturn`
- **吞错完成**：`onErrorComplete`
- **转换异常类型**：`onErrorMap`
- **逐元素恢复**（需谨慎）：`onErrorContinue` + `onErrorStop` 划定范围

---

### 15.6 retry vs retryWhen

| 维度 | retry | retryWhen |
|------|-------|-----------|
| 重试触发 | 任何 onError | 任何 onError（可由策略过滤） |
| 次数控制 | 固定次数或无限 | 由 `Retry` 策略灵活控制 |
| 退避策略 | 无（立即重试） | 支持 `backoff`（指数退避）、`fixedDelay` 等 |
| 重试条件 | 仅次数 | 可基于 `RetrySignal`（异常类型、次数）决策 |
| 终止行为 | 重试耗尽抛原错误 | 由策略决定（可转换错误） |
| 典型场景 | 简单重试 | 网络重试、指数退避 |

**选择建议**：生产环境网络类重试优先 `retryWhen(Retry.backoff(...))`；简单场景用 `retry(n)`。

---

### 15.7 cache vs replay vs share

| 维度 | cache | replay | share |
|------|-------|--------|-------|
| 返回类型 | `Flux<T>` | `ConnectableFlux<T>` | `Flux<T>` |
| 订阅触发 | 首个订阅者自动触发 | 需手动 `connect()` | 首个订阅者触发，全部取消则取消上游 |
| 缓存 | 全部/限定 history/TTL | 全部/限定 history/TTL | 不缓存（新订阅者从当前开始） |
| 多播 | 是 | 是 | 是 |
| 热度 | 热（缓存） | 热（可控制） | 半热（refCount） |
| 典型场景 | 缓存配置/字典 | 多订阅者重放历史 | 共享昂贵上游、无需历史 |

**等价关系**：
- `cache() = replay().autoConnect()`
- `share() = publish().refCount()`

---

### 15.8 Flux 操作符选择决策树

```
开始
 │
 ├─ 要创建源？
 │   ├─ 已有值 → just / fromArray / fromIterable / fromStream
 │   ├─ 范围数 → range
 │   ├─ 空/错误/永不 → empty / error / never
 │   ├─ 编程式（多线程） → create
 │   ├─ 编程式（单线程） → push
 │   ├─ 同步生成 → generate
 │   ├─ 延迟创建 → defer / deferContextual
 │   ├─ 资源管理 → using / usingWhen
 │   ├─ 定时 → interval
 │   └─ 多源合并 → merge / concat / zip / combineLatest / switchOnNext / first*
 │
 ├─ 转换元素？
 │   ├─ 1:1 → map / mapNotNull / cast / ofType
 │   ├─ 1:N（异步） → flatMap（无序）/ concatMap（顺序）/ switchMap（取最新）/ flatMapSequential（并发保序）
 │   ├─ 1:N（同步 Iterable） → flatMapIterable
 │   ├─ 累加中间值 → scan / scanWith
 │   ├─ 灵活 0/1/N → handle
 │   ├─ 分组 → groupBy
 │   ├─ 分批（集合） → buffer / bufferTimeout / bufferUntil / bufferWhile / bufferWhen
 │   ├─ 分批（流） → window / windowTimeout / windowUntil / windowWhile / windowWhen
 │   ├─ 信号↔元素 → materialize / dematerialize
 │   ├─ 加索引 → index
 │   └─ 递归展开 → expand（BFS）/ expandDeep（DFS）
 │
 ├─ 过滤？
 │   ├─ 谓词 → filter / filterWhen（异步）
 │   ├─ 去重 → distinct / distinctUntilChanged
 │   ├─ 取头部 → take / takeLast / takeUntil / takeWhile / takeUntilOther
 │   ├─ 跳头部 → skip / skipLast / skipUntil / skipWhile / skipUntilOther
 │   ├─ 取单个 → elementAt / last / next / single / singleOrEmpty
 │   ├─ 采样 → sample（最新）/ sampleFirst（首个）/ sampleTimeout（去抖）
 │   ├─ 忽略 → ignoreElements
 │   └─ 限量 → limitRate / limitRequest
 │
 ├─ 组合多源？
 │   ├─ 前置 → startWith
 │   ├─ 后置 → concatWith / concatWithValues / thenMany
 │   ├─ 并发合并 → mergeWith / mergeOrderedWith / mergeComparingWith
 │   ├─ 配对 → zipWith / zipWithIterable / withLatestFrom
 │   ├─ 时间窗口 join → join / groupJoin
 │   └─ 竞速 → or
 │
 ├─ 处理错误？
 │   ├─ 回退值 → onErrorReturn
 │   ├─ 切换源 → onErrorResume
 │   ├─ 转换异常 → onErrorMap
 │   ├─ 吞错完成 → onErrorComplete
 │   ├─ 逐元素继续 → onErrorContinue（谨慎）/ onErrorStop（划定范围）
 │   └─ 重试 → retry / retryWhen
 │
 ├─ 副作用/调试？
 │   ├─ 单点 → doOnNext / doOnComplete / doOnError / doOnSubscribe / doOnCancel / doFinally ...
 │   ├─ 全周期 → tap
 │   ├─ 日志 → log
 │   ├─ 堆栈定位 → checkpoint
 │   └─ 监控 → name / tag / metrics
 │
 ├─ 时间控制？
 │   ├─ 延迟元素 → delayElements / delaySequence / delaySubscription / delayUntil
 │   ├─ 超时 → timeout
 │   ├─ 时间标注 → elapsed / timestamp / timed
 │   └─ 缓存/重放 → cache / replay
 │
 ├─ 聚合？
 │   └─ reduce / reduceWith / collect / collectList / collectSortedList / collectMap / collectMultimap / count / all / any / hasElement / hasElements
 │
 ├─ 背压？
 │   ├─ 缓冲 → onBackpressureBuffer
 │   ├─ 丢弃 → onBackpressureDrop
 │   ├─ 报错 → onBackpressureError
 │   └─ 留最新 → onBackpressureLatest
 │
 ├─ 线程/共享？
 │   ├─ 切下游线程 → publishOn
 │   ├─ 切上游线程 → subscribeOn
 │   ├─ 共享上游 → share / publish / cache / replay
 │   ├─ 并行 → parallel + runOn
 │   └─ 取消调度 → cancelOn
 │
 ├─ 上下文？
 │   └─ contextWrite / contextCapture / deferContextual
 │
 └─ 订阅/桥接？
     ├─ 响应式 → subscribe / subscribeWith
     └─ 阻塞 → blockFirst / blockLast / toIterable / toStream
```

---

## 注意事项

1. **方法名差异**：任务列表中的 `combineLatestWith`、`zipIterableWith`（实际为 `zipWithIterable`）、`toFuture`（属 `Mono`）、`debounce`/`throttleFirst`/`throttleLast`（React 用 `sample`/`sampleFirst`/`sampleTimeout`）、`cacheInvalidateIf` 在 reactor-core 3.7.19 的 `Flux.java` 中不存在或命名不同，已在对应章节标注。
2. **装配时 vs 订阅时**：链式调用仅构建处理图，真正数据流动发生在 `subscribe`。涉及状态的转换应使用 `*Deferred`/`*With`（Supplier）版本以避免共享状态。
3. **背压**：默认操作符遵循下游需求；`onBackpressureXxx` 用于源比下游快时的溢出策略；`limitRate`/`limitRequest` 用于精细控制请求量。
4. **线程模型**：`publishOn` 影响其下游执行线程，可多次切换；`subscribeOn` 仅影响最上游订阅线程，多次调用只第一次生效（最靠近源）。
5. **Context 传播方向**：`contextWrite` 从下游向上游传播，`deferContextual`/`transformDeferredContextual` 在上游消费。位置很关键。
6. **错误处理范围**：`onErrorContinue` 影响上游兼容操作符，易泄漏，优先用 `onErrorResume` 在具体内层流处理。
7. **资源清理**：优先使用 `doFinally`（统一处理完成/错误/取消）或 `using`/`usingWhen`（资源生命周期）。
8. **阻塞 API 谨慎使用**：`blockFirst`/`blockLast`/`toIterable`/`toStream` 会阻塞线程并打破响应式模型，仅用于测试或与命令式代码桥接，禁止在响应式链路内部使用。
