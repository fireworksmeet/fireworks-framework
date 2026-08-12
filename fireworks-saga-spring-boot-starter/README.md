# fireworks-saga-spring-boot-starter

基于 **Saga 分布式事务模式** 的 Starter，通过编排本地事务 + 补偿事务，实现跨服务的数据最终一致性。

## 一、Saga 模式概述

`Saga` 是一种在**分布式系统**中管理**跨多个服务的数据一致性**的设计模式，尤其适用于微服务架构
（传统的 ACID 事务 / 2PC 由于性能、耦合度与可用性问题变得不切实际）。

Saga 的核心思想：**将一个庞大的分布式事务拆分成一系列连续的、本地的小事务**，每个本地事务都有其对应的**补偿事务**。

- **本地事务**：在特定服务内可以成功或失败的操作（如库存服务内的扣减库存），是 Saga 中的一个步骤。
- **补偿事务**：用于撤销先前已提交的本地事务效果的操作（如回滚库存）。**补偿事务也必须是幂等的**。

Saga 通过**协调器**按顺序执行本地事务；若其中任一失败，协调器以相反顺序执行之前所有成功步骤的补偿事务，将系统回滚到 Saga 开始前的状态。

## 二、Starter 实现

当前 Starter 通过 `SagaCoordinator`（协调器）将 `SagaContext`（上下文）中提前编排好的 `SagaStep`（步骤）顺序执行，
失败后以相反顺序执行所有已成功步骤的补偿方法。

### 2.1 `SagaStep`（步骤）

定义一阶段正向服务（`execute`）与二阶段补偿服务（`compensate`）：

```java
public interface SagaStep<T> {
    default String getName() { return getClass().getSimpleName(); }
    void execute(T param);            // 一阶段正向服务
    void compensate(T param);         // 二阶段补偿服务
    void compensate(List<T> params);  // 定时批量补偿服务（供 recoveryCompensate 调用）
}
```

实现的子类需注入 Spring IOC 容器，`SagaStepRegistry` 会在容器启动后自动收集所有 `SagaStep`。

### 2.2 `SagaContext`（上下文）

存放编排好的 `SagaStep` 及执行时共享的数据，并生成唯一 `sagaId`：

- 以 `List` 编排时，执行顺序即索引顺序。
- 以 `Map<SagaStep, Integer>` 编排时，相同 `value`（order）的步骤会开启多线程并行执行。

```java
// List 方式
SagaContext<T> ctx = new SagaContext<>(List.of(stepA, stepB), sagaId, data);

// Map 方式：order 相同的步骤并行执行
Map<SagaStep<T>, Integer> steps = Map.of(
        saveOrderStep, 0,
        minusInventoryStep, 1,
        minusHappyCoinStep, 1,   // 与扣库存并行
        finishOrderStep, 2
);
SagaContext<T> ctx = new SagaContext<>(steps, sagaId, data);
```

### 2.3 `SagaLog` 与状态机

每执行一个 `SagaStep`，都会落一条 `SagaLog` 记录执行状态，通过 **CAS 原子更新**保证并发安全：

1. 执行 `execute` 前写入状态 `EXECUTING`（利用 `uk_sagaid_stepname` 唯一索引实现原子拦截，避免重复执行）。
2. `execute` 成功后再用 CAS 将状态更新为 `SUCCEEDED`（确权步骤成功且未被定时任务抢占补偿）。
3. 补偿开始时 CAS 将状态从 `EXECUTING` / `FAILED` → `COMPENSATING`，成功后 → `COMPENSATED`。
4. 所有步骤均成功，整体状态置为 `SUCCEEDED`。

> **注意**：若修改 SagaLog 状态失败或宕机，状态可能停留在 `EXECUTING`。此时可通过
> `SagaLogService.recoveryCompensate()`（在定时任务中调用）进行恢复补偿。

### 2.4 `SagaCoordinator`（协调器）

顺序执行 `SagaContext` 编排的步骤；任一失败即以相反顺序补偿已成功步骤。`execute` 方法对外入口：

```java
sagaCoordinator.execute(sagaContext);
```

## 三、快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-saga-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 初始化数据库

执行 `resources/db.sql`，创建 `saga_log` 表，
`uk_sagaid_stepname` 唯一索引是去重与幂等的关键。

### 3. 实现 `SagaStep`

以下单为例，涉及订单保存、库存扣减、会员喜点扣减、订单完成四个步骤：

```java
@Component
public class SaveOrderSagaStep implements SagaStep<OrderMain> {

    private final OrderMainMapper orderMainMapper;

    @Override
    public void execute(OrderMain orderMain) {
        // 一阶段：保存订单
        orderMain.setState(CREATING);
        orderMainMapper.insert(orderMain);
    }

    @Override
    public void compensate(OrderMain orderMain) {
        // 补偿：将订单置为失败状态
        orderMainMapper.update(null, Wrappers.<OrderMain>lambdaUpdate()
                .eq(OrderMain::getOrderStr, orderMain.getOrderStr())
                .set(OrderMain::getState, CREATED_ERROR));
    }

    @Override
    public void compensate(List<OrderMain> orderMains) {
        // 批量补偿：供 recoveryCompensate 定时调用
        orderMains.forEach(this::compensate);
    }
}
```

其余步骤（扣库存、扣喜点、完成订单）同理实现。

### 4. 编排并执行

```java
@Service
public class OrderMainServiceImpl {

    private final SagaCoordinator sagaCoordinator;
    private final Map<SagaStep<OrderMain>, Integer> steps;

    public OrderMainServiceImpl(SagaCoordinator sagaCoordinator,
                                SaveOrderSagaStep saveOrderStep,
                                MinusInventoryStep minusInventoryStep,
                                MinusHappyCoinStep minusHappyCoinStep,
                                FinishOrderSagaStep finishOrderStep) {
        this.sagaCoordinator = sagaCoordinator;
        // 编排步骤：扣库存与扣喜点（order=1）并行执行
        this.steps = Map.of(
                saveOrderStep, 0,
                minusInventoryStep, 1,
                minusHappyCoinStep, 1,
                finishOrderStep, 2
        );
    }

    public void createOrder(OrderMain orderMain) {
        String sagaId = IdUtil.getUUID();   // 生成唯一 sagaId
        SagaContext<OrderMain> context = new SagaContext<>(steps, sagaId, orderMain);
        sagaCoordinator.execute(context);
    }
}
```

> 参考：`SagaAutoConfiguration` 已自动注册 `SagaStepRegistry`、`SagaLogService`、`SagaCoordinator`，
> 直接注入即可。

### 5. 定时恢复补偿

在调度器中周期调用 `SagaLogService.recoveryCompensate()`，处理宕机/异常遗留的 `EXECUTING` 状态：

```java
// 例：Spring @Scheduled
@Scheduled(fixedDelayString = "PT5M")
public void recover() {
    sagaLogService.recoveryCompensate();
}
```

`recoveryCompensate` 通过 `SagaStepRegistry` 根据 `SagaLog` 保存的步骤名获取对应的 `SagaStep`，
调用其 `compensate(List<T>)` 批量补偿。

## 配置

```yaml
fireworks:
  saga:
    # 见 SagaProperties：重试次数、退避策略等
```
