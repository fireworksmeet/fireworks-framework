## 1. Saga

> `Saga`是一种在<font color="orange">**分布式系统**</font>中管理<font color="orange">**跨多个服务的数据一致性**</font>的设计模式。它尤其适用于<font color="orange">**微服务架构**</font>，因为在这种架构中，传统的ACID事务（通常依赖于两阶段提交`2PC`）由于性能、耦合度和可用性问题而变得不切实际。
>
> Saga的核心思想是：<font color="orange">**将一个庞大的分布式事务拆分成一系列连续的、本地的小事务**</font>。每个本地事务都有其对应的<font color="orange">**补偿事务**</font>。
>
> * **本地事务：**一个在特定服务内可以成功或失败的操作。例如库存服务内的扣减库存操作。它是Saga中的一个步骤。
> * **补偿事务：**一个用于撤销先前已提交的本地事务效果的操作。例如库存服务内的回滚库存操作。<font color="orange">**补偿事务也必须是幂等的**</font>。
>
> Saga通过一个**协调器**来按顺序执行这些本地事务。如果其中任何一个本地事务失败，协调器就会以相反的顺序执行之前所有已成功步骤的补偿事务，从而将系统回滚到Saga开始之前的状态。

## 2. `starter`介绍

> 当前`starter`是对Saga的简单实现。通过`SagaCoordinator`（协调器）将`SagaContext`（上下文）中提前编排好的`SagaStep`（步骤）进行顺序执行，失败后会以相反的顺序执行之前所有已成功执行的`SagaStep`中定义好的补偿方法。

### 2.1 `SagaLog`

> 用于记录每一个`SagaStep`的执行日志。
>
> 1. 在`SagaStep`执行`execute`方法前会向数据库中添加一条`SagaLog`数据，且状态为`EXECUTING`
>
> 2. 在`SagaStep`执行`compensate`方法后，会修改之前保存的`SagaLog`状态。方法执行成功则改为`COMPENSATED`，反之改为`FAILED`。
>
> 3. 当`SagaCoordinator`编排的所有`SagaStep`的所有`execute`方法都被成功执行，则将`SagaLog`的状态改为`SUCCEEDED`.
>
> <font color="orange">注意：当2，3步中修改Saga状态失败或者宕机，此时SagaLog的状态依然是`EXECUTING`，因此重试时要考虑这个状态（不用太担心，已经在对应的`SagaLogService`中提供了简单的实现，即`recoveryCompensate`方法，可在定时器中执行该方法进行重试）</font>

### 2.2 `SagaStep`

> 该接口用于定义一阶段正向服务（`execute`）和二阶段补偿服务（`compensate`）。实现后的子类需要注入到`Spring IOC`容器中，因为`SagaStepRegistry`会从容器中寻找`SagaStep`并进行保存。提供的`SagaLogService`的`recoveryCompensate`就是根据`SagaLog`中保存的名称，去`SagaStepRegistry`中过去对应的`SagaStep`方法，然后执行它的批量补偿方法。

### 2.3 `SagaContext`

> 存放编排好的`SagaStep`和这些`Step`执行时用到的参数。
>
> * 当构造函数中以`List`的方式编排`SagaStep`时，执行顺序就是`List`的索引顺序。
> * 当构造函数中以`Map`的方式编排`SagaStep`时，执行顺序就是`Map`的value顺序，相同value则会开启多线程进行执行。

### 2.4 `SagaCoordinator`

> 用于顺序执行`SagaContext`中编排好的`SagaStep`，失败后会以相反的顺序执行之前所有已成功执行的`SagaStep`中定义好的补偿方法。

## 3. demo

此处以下单为例，整个流程涉及订单的保存、库存的扣减、会员喜点的扣减、订单修改成功状态。

### 3.1 `SagaStep`的实现

#### `SaveOrderSagaStep`

> ```java
> package com.fireworks.steps;
> 
> import com.baomidou.mybatisplus.core.toolkit.Wrappers;
> import com.fireworks.api.entity.Idempotent;
> import com.fireworks.api.enums.IdempotentType;
> import step.com.yzm.fireworks.saga.SagaStep;
> import com.fireworks.entity.OrderDetail;
> import com.fireworks.entity.OrderMain;
> import com.fireworks.mapper.IdempotentMapper;
> import com.fireworks.mapper.OrderDetailMapper;
> import com.fireworks.mapper.OrderMainMapper;
> import lombok.EqualsAndHashCode;
> import lombok.ToString;
> import org.springframework.stereotype.Component;
> import org.springframework.transaction.annotation.Transactional;
> 
> import java.util.List;
> import java.util.stream.Collectors;
> 
> import static com.fireworks.api.enums.OrderStatus.CREATED_ERROR;
> import static com.fireworks.api.enums.OrderStatus.CREATING;
> import static com.yzm.fireworks.common.util.StringPools.COLON;
> 
> /**
>  * @author JYuan
>  */
> @Component
> @ToString
> @EqualsAndHashCode
> public class SaveOrderSagaStep implements SagaStep<OrderMain> {
> 
>     private final OrderMainMapper orderMainMapper;
>     private final OrderDetailMapper orderDetailMapper;
>     private final IdempotentMapper idempotentMapper;
> 
>     public SaveOrderSagaStep(OrderMainMapper orderMainMapper, OrderDetailMapper orderDetailMapper, IdempotentMapper idempotentMapper) {
>         this.orderMainMapper = orderMainMapper;
>         this.orderDetailMapper = orderDetailMapper;
>         this.idempotentMapper = idempotentMapper;
>     }
> 
>     @Override
>     @Transactional(rollbackFor = Exception.class)
>     public void execute(OrderMain orderMain) {
>         String uniqueStr = orderMain.getUniqueStr();
>         Idempotent idempotent = Idempotent.builder().idempotentKey(uniqueStr + COLON + IdempotentType.CREATE_ORDER.getValue()).build();
>         int isInsert = idempotentMapper.insertIgnore(idempotent);
>         if (isInsert != 0) {
>             orderMain.setState(CREATING);
>             // 生成订单
>             orderMainMapper.insert(orderMain);
>             List<OrderDetail> orderDetails = orderMain.getOrderDetails();
>             orderDetails.forEach(item -> {
>                 item.setOrderId(orderMain.getId());
>                 item.setOrderStr(orderMain.getOrderStr());
>             });
>             // 生成订单详情
>             orderDetailMapper.insertBatch(orderDetails);
>         } else {
>             OrderMain isExist = orderMainMapper.selectOne(Wrappers.<OrderMain>lambdaQuery().eq(OrderMain::getUniqueStr, orderMain.getUniqueStr()));
>             orderMain.setOrderStr(isExist.getOrderStr());
>             orderMain.setState(isExist.getState());
>         }
>     }
> 
>     @Override
>     @Transactional(rollbackFor = Exception.class)
>     public void compensate(OrderMain orderMain) {
>         String orderStr = orderMain.getOrderStr();
>         orderMainMapper.update(null, Wrappers.<OrderMain>lambdaUpdate().eq(OrderMain::getOrderStr, orderStr).set(OrderMain::getState, CREATED_ERROR));
>     }
> 
>     @Override
>     @Transactional(rollbackFor = Exception.class)
>     public void compensate(List<OrderMain> orderMains) {
>         List<String> orderStrs = orderMains.stream().map(OrderMain::getOrderStr).collect(Collectors.toList());
>         orderMainMapper.update(null, Wrappers.<OrderMain>lambdaUpdate().in(OrderMain::getOrderStr, orderStrs).set(OrderMain::getState, CREATED_ERROR));
>     }
> }
> ```

#### `MinusInventoryStep`

> ```java
> package com.fireworks.steps;
> 
> import com.google.common.collect.Lists;
> import com.fireworks.api.request.GoodsRequest;
> import com.fireworks.api.request.InventoryRequest;
> import com.yzm.fireworks.api.Result;
> import com.yzm.fireworks.saga.step.SagaStep;
> import com.fireworks.client.GoodsClient;
> import com.fireworks.entity.OrderDetail;
> import com.fireworks.entity.OrderMain;
> import lombok.EqualsAndHashCode;
> import lombok.ToString;
> import org.springframework.stereotype.Component;
> 
> import java.util.List;
> 
> import static com.fireworks.api.enums.OrderStatus.CREATING;
> import static com.fireworks.service.impl.OrderMainServiceImpl.handleFeignResult;
> 
> /**
>  * @author JYuan
>  */
> @Component
> @ToString
> @EqualsAndHashCode
> public class MinusInventoryStep implements SagaStep<OrderMain> {
> 
>     private final GoodsClient goodsClient;
> 
>     public MinusInventoryStep(GoodsClient goodsClient) {
>         this.goodsClient = goodsClient;
>     }
> 
>     @Override
>     public void execute(OrderMain orderMain) {
>         if (CREATING.equals(orderMain.getState())) {
>             InventoryRequest inventoryRequest = getInventoryRequest(orderMain);
>             // 扣减库存
>             Result<?> goodsResult = goodsClient.minusInventory(inventoryRequest);
>             handleFeignResult(goodsResult);
>         }
>     }
> 
>     @Override
>     public void compensate(OrderMain orderMain) {
>         InventoryRequest inventoryRequest = getInventoryRequest(orderMain);
>         Result<?> goodsResult = goodsClient.addInventory(inventoryRequest);
>         handleFeignResult(goodsResult);
>     }
> 
>     @Override
>     public void compensate(List<OrderMain> orderMains) {
>         for (OrderMain orderMain : orderMains) {
>             compensate(orderMain);
>         }
>     }
> 
>     private InventoryRequest getInventoryRequest(OrderMain orderMain) {
>         String orderStr = orderMain.getOrderStr();
>         InventoryRequest inventoryRequest = InventoryRequest.builder().idempotentKey(orderStr).goods(Lists.newArrayList()).build();
>         List<OrderDetail> orderDetails = orderMain.getOrderDetails();
>         for (OrderDetail orderDetail : orderDetails) {
>             orderDetail.setOrderStr(orderStr);
>             inventoryRequest.getGoods().add(GoodsRequest.builder().goodsId(orderDetail.getGoodsId()).inventory(orderDetail.getQuantity()).build());
>         }
>         return inventoryRequest;
>     }
> 
> }
> ```

#### `MinusHappyCoinStep`

> ```java
> package com.fireworks.steps;
> 
> import com.fireworks.api.request.HappyCoinRequest;
> import com.yzm.fireworks.api.Result;
> import com.yzm.fireworks.saga.step.SagaStep;
> import com.fireworks.client.UserClient;
> import com.fireworks.entity.OrderDetail;
> import com.fireworks.entity.OrderMain;
> import lombok.EqualsAndHashCode;
> import lombok.ToString;
> import org.springframework.stereotype.Component;
> 
> import java.util.List;
> 
> import static com.fireworks.api.enums.OrderStatus.CREATING;
> import static com.fireworks.service.impl.OrderMainServiceImpl.handleFeignResult;
> 
> /**
>  * @author JYuan
>  */
> @Component
> @ToString
> @EqualsAndHashCode
> public class MinusHappyCoinStep implements SagaStep<OrderMain> {
> 
>     private final UserClient userClient;
> 
>     public MinusHappyCoinStep(UserClient userClient) {
>         this.userClient = userClient;
>     }
> 
>     @Override
>     public void execute(OrderMain orderMain) {
>         if (CREATING.equals(orderMain.getState())) {
>             List<OrderDetail> orderDetails = orderMain.getOrderDetails();
>             long totalHappy = 0;
>             for (OrderDetail orderDetail : orderDetails) {
>                 totalHappy += orderDetail.getHappyCoin();
>             }
>             String orderStr = orderMain.getOrderStr();
>             if (totalHappy > 0) {
>                 HappyCoinRequest happyCoinRequest = HappyCoinRequest.builder().userId(orderMain.getBuyerId()).happyCoin(totalHappy).idempotentKey(orderStr).build();
>                 Result<?> result = userClient.minusHappyCoin(happyCoinRequest);
>                 handleFeignResult(result);
>             }
>         }
>     }
> 
>     @Override
>     public void compensate(OrderMain orderMain) {
>         List<OrderDetail> orderDetails = orderMain.getOrderDetails();
>         long totalHappy = 0;
>         for (OrderDetail orderDetail : orderDetails) {
>             totalHappy += orderDetail.getHappyCoin();
>         }
>         String orderStr = orderMain.getOrderStr();
>         if (totalHappy > 0) {
>             HappyCoinRequest happyCoinRequest = HappyCoinRequest.builder().userId(orderMain.getBuyerId()).happyCoin(totalHappy).idempotentKey(orderStr).build();
>             Result<?> result = userClient.addHappyCoin(happyCoinRequest);
>             handleFeignResult(result);
>         }
>     }
> 
>     @Override
>     public void compensate(List<OrderMain> orderMains) {
>         for (OrderMain orderMain : orderMains) {
>             compensate(orderMain);
>         }
>     }
> 
> }
> ```

#### `FinishOrderSagaStep`

> ```java
> package com.fireworks.steps;
> 
> import com.baomidou.mybatisplus.core.toolkit.Wrappers;
> import com.yzm.fireworks.saga.service.SagaLogService;
> import com.yzm.fireworks.saga.step.SagaStep;
> import com.fireworks.entity.OrderMain;
> import com.fireworks.mapper.OrderMainMapper;
> import lombok.EqualsAndHashCode;
> import lombok.ToString;
> import org.springframework.stereotype.Component;
> import org.springframework.transaction.annotation.Transactional;
> 
> import java.util.List;
> 
> import static com.fireworks.api.enums.OrderStatus.CREATING;
> import static com.fireworks.api.enums.OrderStatus.WAIT_PAY;
> 
> 
> /**
>  * @author JYuan
>  */
> @Component
> @ToString
> @EqualsAndHashCode
> public class FinishOrderSagaStep implements SagaStep<OrderMain> {
> 
>     private final OrderMainMapper orderMainMapper;
>     private final SagaLogService sagaLogService;
> 
>     public FinishOrderSagaStep(OrderMainMapper orderMainMapper, SagaLogService sagaLogService) {
>         this.orderMainMapper = orderMainMapper;
>         this.sagaLogService = sagaLogService;
>     }
> 
>     @Override
>     @Transactional(rollbackFor = Exception.class)
>     public void execute(OrderMain orderMain) {
>         if (CREATING.equals(orderMain.getState())) {
>             String orderStr = orderMain.getOrderStr();
>             // 将订单状态改为待支付
>             orderMainMapper.update(null, Wrappers.<OrderMain>lambdaUpdate().eq(OrderMain::getOrderStr, orderStr).set(OrderMain::getState, WAIT_PAY));
>             orderMain.setState(WAIT_PAY);
>         }
>     }
> 
>     @Override
>     public void compensate(OrderMain orderMain) {
>     }
> 
>     @Override
>     public void compensate(List<OrderMain> orderMains) {
>     }
> }
> ```

### 3.2 `SagaCoordinator`的使用

> ```java
> package com.fireworks.service.impl;
> 
> import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
> import com.google.common.collect.ImmutableMap;
> import com.fireworks.boot.id.SnowflakeIdGenerator;
> import com.yzm.fireworks.saga.step.SagaContext;
> import com.yzm.fireworks.saga.step.SagaCoordinator;
> import com.yzm.fireworks.saga.step.SagaStep;
> import com.fireworks.entity.OrderDO;
> import com.fireworks.entity.OrderMain;
> import com.fireworks.mapper.OrderMainMapper;
> import com.fireworks.service.IOrderMainService;
> import com.fireworks.steps.FinishOrderSagaStep;
> import com.fireworks.steps.MinusHappyCoinStep;
> import com.fireworks.steps.MinusInventoryStep;
> import com.fireworks.steps.SaveOrderSagaStep;
> import lombok.extern.slf4j.Slf4j;
> import org.springframework.stereotype.Service;
> 
> import java.util.Map;
> 
> 
> /**
>  * @author JYuan
>  */
> @Service
> @Slf4j
> public class OrderMainServiceImpl extends ServiceImpl<OrderMainMapper, OrderMain> implements IOrderMainService {
> 
>     private final SnowflakeIdGenerator idGenerator;
>     private final Map<SagaStep<OrderMain>, Integer> steps;
>     private final SagaCoordinator sagaCoordinator;
> 
>     public OrderMainServiceImpl(SnowflakeIdGenerator idGenerator, SaveOrderSagaStep saveOrderSagaStep, FinishOrderSagaStep finishOrderSagaStep, MinusInventoryStep minusInventoryStep, MinusHappyCoinStep minusHappyCoinStep, SagaCoordinator sagaCoordinator) {
>         this.idGenerator = idGenerator;
>         // 获取容器中注册好的协调器
>         this.sagaCoordinator = sagaCoordinator;
>         // 编排好SagaStep
>         this.steps = ImmutableMap.<SagaStep<OrderMain>, Integer>builder()
>                 .put(saveOrderSagaStep, 0)
>                 .put(minusInventoryStep, 1)
>                 .put(minusHappyCoinStep, 1)
>                 .put(finishOrderSagaStep, 2)
>                 .build();
>     }
>     
>     @Override
>     public OrderDO createOrderWithSaga(OrderMain orderMain) {
>         String sagaId = idGenerator.getStr();
>         SagaContext<OrderMain> sagaContext = new SagaContext<>(steps, sagaId, orderMain);
>         orderMain.setOrderStr(idGenerator.getStr());
>         sagaCoordinator.execute(sagaContext);
>         return new OrderDO(orderMain.getOrderStr(), orderMain.getState());
>     }
>     
> }
> ```