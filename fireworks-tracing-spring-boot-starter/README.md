# fireworks-tracing-spring-boot-starter

链路追踪 Starter：引入 Micrometer Tracing（OTel 桥接），并把 `traceId` / `spanId` 填进
`fireworks-common` 预留的日志关联段。**由 `fireworks-webmvc` 默认引入**，普通业务工程无需单独声明。

## 为什么是独立模块

不是代码多，而是这两件事都不该落在 `fireworks-common` 上：

1. **它会把 actuator 带进所有工程**。Boot 的 Tracing / Observation 自动配置位于
   `spring-boot-actuator-autoconfigure`，缺了它 `micrometer-tracing` 装配不出任何 `Tracer`，所以
   `spring-boot-starter-actuator` 是硬依赖。而 common 是所有模块的底座（缓存、ID、消息、定时任务…… 其中
   大量是非 Web 应用），让它们为此背上 actuator 与 OTel SDK 并不合适。
2. **关联段的键名属于追踪实现，不属于日志格式**。`traceId` / `spanId` 是 Micrometer 的约定；写在 common 里
   等于让日志模块替追踪实现做主，换成 Agent 探针（`trace_id` / `span_id`）时就会一直渲染空值且不报错。

## 做了什么

只做三件事：

1. **引入追踪实现**：`micrometer-tracing-bridge-otel`，让 Boot 的 Observation / Tracing 体系真正生效
   （Servlet 入站、`RestClient` / `WebClient` / `RestTemplate` 出站、跨线程传递）。
2. **注入日志关联段**：`logging.pattern.correlation = [%X{traceId:-},%X{spanId:-}]`，对应 common 格式串里的占位。
3. **注册异步上下文传播装饰器**：把 `ContextPropagatingTaskDecorator` 注册为容器中唯一的 `TaskDecorator`，
   让 `@Async` 与业务线程池继承提交线程的 Observation / TraceContext，见下节。

**不注入采样率**：采样率只决定 span 是否被记录并上报，与 traceId 是否生成、是否写入 MDC 无关
（OTel 桥在 scope attach 时无条件把 `SpanContext#getTraceId()` 写入 MDC）。本模块不含 exporter，采样率在这里
没有任何可观测效果，因此不做设置，沿用 Boot 默认值 `0.1`。

**不引入 exporter**：span 只在进程内生成并写入 MDC，不上报、不落盘、不产生网络开销。

## 异步上下文传播

`ContextPropagatingTaskDecorator` 只负责把**容器中已注册的** `ThreadLocalAccessor` 捕获并还原，本身不带任何
accessor。当前 classpath 上唯一自动注册的 accessor 是 `ObservationThreadLocalAccessor`，它随
`micrometer-observation` 进入——而 `micrometer-observation` 只由本模块带进来。所以这个装饰器放在
`fireworks-common` 只会是「装了不生效」的假能力：Bean 在，accessor 列表为空，异步任务什么也拿不到。

注册后由 Boot 的 `ThreadPoolTaskExecutorBuilder` 自动应用到容器中**唯一**的 `TaskDecorator`：

- `@Async` 的默认线程池 `applicationTaskExecutor` 自动继承上下文；
- 业务自建线程池用 `ThreadPoolTaskExecutorBuilder` 声明为 Bean，同样自动带上该装饰器；
- 容器中出现两个 `TaskDecorator` 会直接抛 `NoUniqueBeanDefinitionException`。业务若要自定义装饰器，
  自定义 Bean 会覆盖本模块的（`@ConditionalOnMissingBean`），此时需要自行 `delegate` 给
  `ContextPropagatingTaskDecorator`，否则链路会在异步边界断裂。

> **这也是“异步上下文传播随 tracing 提供”的原因**：没有引入本模块的工程（如纯 xxl-job 批处理服务）
> 不会获得该 Bean，同时也确实没有任何 tracing 上下文可传，能力与语义保持一致。

## 日志格式

引入后无需额外配置即可看到（格式由 `fireworks-common` 提供）：

```text
2026-10-16T10:20:30.123+08:00  INFO 12345 --- [8f3c1a2b9d4e6f70,3a1b2c4d5e6f7081] [http-nio-8080-exec-1] c.y.demo.OrderController : create order success
```

## 启动诊断

启动完成时打印一行 `[fireworks-tracing]` 前缀的状态。链路追踪是典型的「静默失败」——取不到 traceId 时只会
渲染成空、不会报错，这一行用来把问题显性化：

```text
[fireworks-tracing] 链路追踪已启用 — 未配置 exporter，span 不上报，仅提供日志用的 traceId
[fireworks-tracing] 链路追踪已启用 — 采样率=0.1，span 将上报到远端
[fireworks-tracing] 链路追踪已关闭（management.tracing.enabled=false），日志中不会出现 traceId
```

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `logging.pattern.correlation` | `[%X{traceId:-},%X{spanId:-}]`（本模块注入） | 日志关联段；换追踪实现时改这里对齐键名 |
| `management.tracing.enabled` | `true` | 总开关；关闭后日志中不再出现 traceId |
| `management.tracing.sampling.probability` | `0.1`（Boot 默认） | 采样率；只影响 span 是否上报，**无 exporter 时无意义** |
| `management.tracing.propagation.type` | `W3C` | 跨进程传播格式；对接 APISIX 等网关时保持 W3C，若网关使用 B3 需显式声明 |

## 需要调用树时再加 exporter

框架不替业务决定上报到哪。要按 traceId 看完整调用链（性能归因），自行加依赖：

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
management:
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
```

> 引入 exporter 意味着部署中**必须存在 collector**，否则应用会持续尝试连接默认的 `localhost:4318` 并刷错误日志。
> 采样率**从这一刻起才真正生效**，按上报成本与排查需求调整 `management.tracing.sampling.probability`。

## 与 Agent 方案的取舍

本模块走**依赖模式**（改 pom 即生效），而非 Java Agent 探针模式：

- **依赖模式**：本地开发天然生效、不牵动部署、覆盖 Spring 管理的全部边界；不覆盖 JDBC / Redis 等非 Spring 组件的自动埋点。
- **Agent 模式**：零代码、覆盖任意客户端与线程模型；但每台开发机与 CI 都需挂载 JVM 参数，且**不能与本模块同时启用**（会产生重复 span）。

若后续切换到 Agent，业务代码无需改动，但需要做两件事：把本模块（它由 `fireworks-webmvc` 默认引入，需在自己的
依赖里排除）移除，并按 Agent 写入 MDC 的键名覆盖日志关联段——OTel Agent 默认是 `trace_id` / `span_id`：

```yaml
logging:
  pattern:
    correlation: '[%X{trace_id:-},%X{span_id:-}]'
```
