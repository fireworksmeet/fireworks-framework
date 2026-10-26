# fireworks-outbox-spring-boot-starter

**事务性发件箱（Transactional Outbox）** Starter：把「业务数据落库」与「消息最终会被投递」绑定在同一个本地事务里，
用 `FOR UPDATE SKIP LOCKED` 保证多实例并发投递不重叠。

> 它解决的是 **消息可靠性**，不是分布式事务（不涉及 XA / 2PC）。
> 语义是 **At-Least-Once，不是 Exactly-Once**——所以 **消费端必须幂等**，这是使用本组件的前提，不是可选项。

## 定位

```text
简单场景（跨服务通知、引用绑定、孤儿回收）：业务事务 + Outbox 就够了，不需要任何流程编排
复杂场景（并行分支、超时、补偿、人工介入）：在 Outbox 之上再叠加编排引擎（如嵌入式 Flowable）
```

Outbox 是「业务事务」与「消息基础设施」之间的稳定边界：业务永远只调用 `outboxService.append(...)`，
未来从 HTTP/RPC 投递演进到 Kafka / CDC，业务代码不需要改动。

## 功能特性

- **同事务落库**：业务写 + 事件写在同一事务，提交则都在、回滚则都不在。
- **并发安全领取**：PostgreSQL `FOR UPDATE SKIP LOCKED`，多实例可同时运行，无需 Redis 锁。
- **领取与执行分离**：领取是独立原子短事务，远程调用在事务外执行，绝不持锁等待远程响应。
- **指数退避重试 + jitter**：打散退避到期造成的惊群；可区分可重试 / 不可重试。
- **PROCESSING 超时恢复**：进程宕机后停留在 PROCESSING 的事件会被重置重放（因此必须幂等）。
- **消费端幂等用 `event_id`**：重试、恢复、重放都是**同一行**重新投递，`event_id` 不变，对端以它建唯一约束即可。
- **DEAD 终态**：重试耗尽 / 不可重试的事件进入 DEAD，等待人工介入，不无限重试。
- **链路追踪**：写入时记录 `trace_id`，投递时恢复 MDC，串起「请求 → 业务事务 → Outbox → Handler → 远程服务」。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-outbox-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 初始化数据库

按数据库选择建表脚本（模块根 `script/db/` 下，随源码交付、不进 jar）：

| 数据库 | 脚本 |
| --- | --- |
| PostgreSQL 12+ | `script/db/outbox_event.postgresql.sql` |
| MySQL 8.0.1+ | `script/db/outbox_event.mysql.sql` |

两份脚本的列类型一致（`event_id VARCHAR(32)`、`payload TEXT`），Java 侧**共用一套 SQL**，不需要方言；消费端幂等直接用 `event_id`。

### 3. 编写 Handler

一个 `eventType` 对应一个 `OutboxHandler`（Spring 容器自动收集）：

```java
@Component
public class FileReferenceAddHandler implements OutboxHandler {

    private final FileCenterClient fileCenterClient;

    @Override
    public String eventType() {
        return "FILE_REFERENCE_ADD";
    }

    @Override
    public void handle(OutboxEvent event) {
        FileBindPayload payload = JsonUtil.deserialize(event.getPayload(), FileBindPayload.class);
        // eventId 在同一条消息重投时不变，对端以它建唯一约束即可
        fileCenterClient.addReference(event.getEventId(), payload.userId(), payload.fileKey());
    }
}
```

- 抛普通异常 → 退避重试，超限进 DEAD；
- 抛 `OutboxNonRetryableException` → 直接进 DEAD（参数非法、权限不足等）。

### 4. 在业务事务中写入

```java
@Service
@RequiredArgsConstructor
public class UserAvatarService {

    private final UserRepository userRepository;
    private final OutboxService outboxService;

    @Transactional
    public void bindAvatar(Long userId, String fileKey) {
        // 1. 业务数据
        userRepository.updateAvatar(userId, fileKey);
        // 2. 同一事务写入 outbox：提交则两者都在，回滚则两者都不在
        outboxService.append(OutboxAppendRequest.builder()
                .eventType("FILE_REFERENCE_ADD")
                .entityType("USER").entityId(userId.toString())
                .payload(Map.of("userId", userId, "fileKey", fileKey))
                .build());
    }
}
```

> **不要在 `append` 前后做远程调用**。文件上传这类场景应先传对象存储拿到 key，
> 再在本地事务里「落库 + 写 outbox」；对象存储侧的孤儿文件用宽限期 + 清理任务兜底。

### 5. 配置（可选，均有默认值）

```yaml
fireworks:
  outbox:
    enabled: true
    trace-id-key: traceId
    worker:
      enabled: true               # 关闭则只入库不投递（测试环境常用）
      batch-size: 100
      max-concurrency: 4          # Handler 执行线程数
      queue-capacity: 200
      initial-delay: 1s
      fixed-delay: 1s
    lanes:                          # 可选：按事件类型分道，避免互相拖住
      - name: order
        event-types: [ORDER_CREATED, ORDER_PAID]
        max-concurrency: 8          # 未填则继承 worker.max-concurrency
        batch-size: 50              # 未填则继承 worker.batch-size
        queue-capacity: 200         # 未填则继承 worker.queue-capacity
    recovery:
      enabled: true
      interval: 1m
      timeout: 5m                 # 必须大于 Handler 最长执行时间
      initial-delay: 10s
    retry:
      max-retry-count: 20
      base-delay: 1s
      max-delay: 30m
      jitter: 500ms
```

## 分道：按事件类型隔离

默认只有一个道，领取严格按 `id` 顺序。某类事件积压时，后写入的事件（例如订单）要等积压被领完才轮得到——这就是 FIFO 的队头阻塞。

`lanes` 把指定 `event-type` 交给独立的领取条件与线程池：

- 默认道会自动排除所有已分道的类型，因此**每条事件只会被一个道领取**；
- 每个道有自己的 `batch-size` / `max-concurrency` / `queue-capacity`，未填的继承 `worker`；
- 道名与事件类型在启动期校验：重名、重复分道、空 `event-types` 都会**启动失败**；
- 线程总数 = Σ(各道 `max-concurrency`) × 实例数，配置时注意总量；
- 各道线程池的并发/队列只由 `worker` 与 `lanes` 决定，**不读取** `spring.task.execution.*`（内部池不受业务全局配置影响）；
- 分道不改变 at-least-once 语义，也不保证跨道顺序。

**分道不解决的问题**：如果某个道的 Handler 本身很慢（批量导出、循环调接口），它只会在自己的道里排队。这类任务不应该由 Outbox 直接执行，应该只发一条「任务已创建」的事件，交给任务系统异步处理。

## 立即触发：提交后唤醒

默认按 `fixed-delay` 轮询。需要立刻投递时，调用 `OutboxWorker#wakeUp()` 跳过本次间隔——**何时调用由业务决定**，框架不自动触发：

```java
@Transactional
public void createOrder(...) {
    outboxService.append("ORDER_CREATED", payload);

    // 提交后再唤醒；提交前调用只是白跑一次（行对其它连接不可见），不会出错
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            outboxWorker.wakeUp();
        }
    });
}
```

| 项 | 说明 |
| --- | --- |
| 谁执行 | 仍是 Worker 去 `claim`，业务不直接调 Handler —— 不存在「手动执行 + 轮询执行」的竞争 |
| 合并 | 短时间内多次调用只触发一轮，批量写入不会打爆调度器 |
| 范围 | 只唤醒**本实例**；其他实例仍按各自轮询节奏 |
| 代价 | 多一轮 `claim`（每个道一条查询） |
| 丢失 | 尽力而为：应用正在停止时会被忽略，未投递事件仍由轮询与恢复任务兜底 |

> 框架不提供 `afterCommit` 的封装：它属于业务对时机的判断，且不同场景（单条写入、批量写入、MQ 消费后写入）触发点不同。

## 运行链路

```text
HTTP
  ↓
@Transactional
  ↓
业务表 + outbox_event（status=10 PENDING）   ← 同一本地事务
  ↓ COMMIT
投递 Worker（按道）：claim(batchSize)  →  status=20 PROCESSING
  ↓ 事务外
OutboxHandler.handle()  →  远程服务
  ↓
SUCCEEDED(30) / 重试→PENDING(10) / DEAD(40)
```

进程宕机导致事件停留在 PROCESSING 时，恢复任务会把「领取时间早于 `now - timeout`」的事件重置回 PENDING 重放。
恢复同样计入一次重试：未超限的重投，达到 `max-retry-count` 的直接置 DEAD——否则进程反复崩溃时，同一条事件会被无限重投。

## 状态与流转

| 编码 | 状态 | 说明 |
| --- | --- | --- |
| 10 | `PENDING` | 待投递（含等待重试） |
| 20 | `PROCESSING` | 已被某 Worker 领取，执行中 |
| 30 | `SUCCEEDED` | 投递成功（终态） |
| 40 | `DEAD` | 重试耗尽 / 不可重试（终态，需人工介入） |

所有状态回写都带 `status = 20 AND claimed_by = workerId` 的 CAS 条件：更新行数为 0 表示已失去持有权，
此时放弃写入，绝不覆盖别人的执行结果。

## 核心类速览

| 类 | 说明 |
| --- | --- |
| `OutboxService` / `DefaultOutboxService` | 写入入口，必须在业务本地事务内调用 |
| `OutboxHandler` | 业务侧处理器 SPI，按 `eventType` 注册 |
| `OutboxHandlerRegistry` | 处理器注册表，重复 `eventType` 启动即 fail-fast |
| `OutboxEventRepository` | 持久化契约（`JdbcOutboxEventRepository`）：insert / claim / mark / recover |
| `OutboxDispatcher` | 单条事件投递与结果回写 |
| `OutboxWorker` | 按道定时领取 + 并发投递（每道独立线程池，CallerRunsPolicy 背压） |
| `OutboxLane` | 一个投递道：事件类型过滤 + 独立线程池 |
| `OutboxRecoveryWorker` | PROCESSING 超时恢复 |
| `OutboxRetryPolicy` / `ExponentialOutboxRetryPolicy` | 指数退避 + jitter |

## 注意事项

- **消费端必须幂等**：用 `event_id` 建唯一约束。重试、PROCESSING 超时恢复、重放都是**同一行**重新投递，`event_id` 不变。
- **不要指望 outbox 做业务去重**：重复的业务操作会在业务表唯一约束处失败，同一事务里的 outbox 写入随之回滚。
  若某个操作没有业务行可挂唯一键，应在业务侧建幂等记录，而不是给 outbox 增加"业务幂等键"。
- **`recovery.timeout` 必须大于 Handler 最长执行时间**，否则会把执行中的事件误判为超时并重复投递。
- **每轮领取量按线程池余量收敛**：只领「空闲线程 + 队列剩余」条，避免“领了却排在队列里”白耗 `recovery.timeout` 的预算。
- **两个 Worker 都由 `SmartLifecycle` 管理**：容器刷新末尾启动（比 Web 端口监听早一步），关闭时对称停止；
  轮询线程是 daemon，应用退出不会因为它们在跑而挂住。
- **支持矩阵**：PostgreSQL 12+ / MySQL 8.0.1+（领取依赖 `SKIP LOCKED`，两者都支持）。
- **不依赖任何 ORM**：只依赖 Spring JDBC 与一个主 `DataSource`，业务用什么 ORM 与本组件无关。
- **只有一份 SQL**：领取为「短事务内 `SELECT ... FOR UPDATE SKIP LOCKED` + `UPDATE`」，
  不使用 PostgreSQL 独有的 `RETURNING`；列类型也已统一（`event_id VARCHAR(32)`、`payload TEXT`），
  因此不存在方言层——只有建表脚本按库各一份。
- **接入其他数据库**：需要 `SKIP LOCKED` 等价能力（Oracle 11g+ / SQL Server 用 `READPAST`），
  届时直接替换 `JdbcOutboxEventRepository`（它只依赖 `OutboxEventRepository` 契约）。
- **不保证顺序**：投递是并发的，同一实体的多条消息也可能并行或乱序；确有需要时在 Handler 内按 `entity_id` 自行串行（或让消息带上业务序列号）。
