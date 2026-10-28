# 应用结构与编码红线

> 定位：应用的结构与运行时约定——设计原则、Spring Boot 使用、分层与依赖方向、命名、事务、并发与一致性、依赖注入、配置、生命周期、AOP、异步、定时任务、缓存、外部调用、依赖管理、安全、性能。
> 边界：API 契约见 `api.md`；表与 SQL 设计见 `database.md`；MyBatis 约定见 `mybatis.md`；一致性与补偿见 `reliability.md`；日志与测试见 `observability.md`、`testing.md`；语言层写法见 `java.md`。
>
> 规范级别：【强制】违反即视为代码问题；【推荐】默认遵守；【禁止】原则上不得使用；【说明】背景解释。

---

# 一、总体设计原则

- 【强制】单一职责：一个类只承担一个清晰的业务职责。
- 【强制】业务逻辑必须有明确归属；**禁止**为了"方便"把业务逻辑堆到 Controller、Utils、Config 或各种 Manager 中。
- 【强制】优先简单、直接、易理解的实现；抽象的动机必须是**真实的业务变化**，**禁止**为了"设计模式""高扩展性"或对未来的预测引入无实际收益的抽象层。
- 【推荐】优先组合而不是继承；优先显式依赖而不是隐式全局状态。
- 【强制】一个功能必须能回答三个问题：**入口在哪里、核心业务在哪里、数据在哪里持久化**。
- 【强制】核心业务必须能被单元测试独立验证，不得强依赖静态工具、全局变量或难以替换的外部资源。

---

# 二、Spring Boot 使用原则

- 【强制】使用 Spring Boot 的自动配置与标准能力，不重复实现框架已有能力；**禁止**无理由关闭自动配置。
- 【说明】自动配置类统一命名 `XxxAutoConfiguration`；但与 Boot / 三方库已有的同名类冲突时，保留 `Fireworks` 前缀（如 `FireworksRedisAutoConfiguration`、`FireworksMybatisPlusAutoConfiguration`），否则日志与异常栈会指向错误的那一个。
- 【强制】纯开关（`enabled`）**不在 `@ConfigurationProperties` 类里留字段**（除非代码真的读取它）：装配由 `@ConditionalOnProperty` 决定，IDE 提示通过 `META-INF/additional-spring-configuration-metadata.json` 提供。否则字段与注解会成为两套默认值，容易被误当作第二个开关。
- 【推荐】启动类放在项目根包下，使组件扫描范围清晰。
- 【禁止】使用默认包（不声明 `package`）。

---

# 三、分层与依赖方向

## 3.1 分层

```text
Controller
    ↓
Service（业务编排 + 事务）
    ↓
数据访问层（Mapper / Repository）
    ↓
Database / Redis / 外部系统
```

【强制】依赖只能**向下**，禁止跨层调用与反向依赖。

## 3.2 Controller

- 【强制】只负责协议适配：参数接收、参数校验、调用业务、返回响应。
- 【禁止】编写核心业务逻辑。
- 【禁止】绕过 Service 直接调用数据访问层、Redis、MQ 或第三方 HTTP 客户端；**禁止**在 Controller 处理事务。

```java
// 错误
@PostMapping(REQUEST_MAPPING + "/match")
public Result<?> match(@RequestBody MatchRequest request) {
    User user = userMapper.selectById(request.userId());
    // ...业务处理...
    userProfileMapper.insert(...);
    redisTemplate.delete(...);
    return Result.ok();
}

// 正确
@PostMapping(REQUEST_MAPPING + "/match")
public Result<MatchResponse> match(@Valid @RequestBody MatchRequest request) {
    return Result.ok(matchService.match(request));
}
```

## 3.3 Service

- 【强制】Service 是业务编排与业务规则的主要承载层。
- 【强制】Service 不得感知 HTTP 层细节（`HttpServletRequest`、HTTP Header 等）。
- 【强制】Service **禁止返回统一响应体**（`Result`），返回业务对象或 `void`；错误通过抛业务异常处理。
- 【强制】业务断言使用 `BizAssert`，**禁止**到处 `throw new BizException`。
- 【强制】跨多个数据访问对象的原子操作由 Service 开启事务（规则见 §五）。
- 【推荐】一个方法对应一个明确的业务动作。
- 【禁止】大量纯透传方法（`getById()` / `save()`），那说明 Service 只是无意义代理层。

## 3.4 数据访问层

- 【强制】只负责数据访问，不承载业务规则。
- 【禁止】根据业务状态做复杂分支判断。
- 【禁止】查询结果直接作为 API 响应返回。
- 【推荐】方法名表达查询语义（如 `findActiveUserByPhone()`），避免 `selectSomething()` / `queryData()`。

> SQL 书写与 ORM 约定见 `mybatis.md`；对象转换见 §3.5。

## 3.5 对象转换（MapStruct）

- 【强制】各层对象之间的转换统一使用 **MapStruct**（`componentModel = SPRING`）。
- 【禁止】手写大量 `set` / `get` 赋值。
- 【禁止】使用 `BeanUtils.copyProperties`（反射、性能差，字段名不一致时**静默漏拷**）。

### 3.5.1 位置与命名

- 【强制】转换器放在 `{模块}/model/convert/` 包下。
- 【强制】类型命名统一为 **`XxxConverter`**，禁止 `XxxConvert` / `XxxMapper` / `XxxMapStruct`。
  【说明】禁止以 `Mapper` 结尾：`@MapperScan` 通常按整包扫描，以 `Mapper` 命名的接口有被 ORM **误识别**的风险。
- 【禁止】把转换器放进 `mapper` 包（与数据访问接口混放）。

两个同名 `@Mapper` 的区别：

```java
org.apache.ibatis.annotations.Mapper   // ORM —— 【禁止】使用
org.mapstruct.Mapper                   // MapStruct —— 【必须】使用
```

- 【强制】数据访问 Mapper 接口禁止 `@Mapper`（由 `@MapperScan` 统一注册，见 `mybatis.md`）：
- 【强制】MapStruct 转换器必须标注 `org.mapstruct.Mapper`；
- 【说明】两者包路径不同，`@MapperScan` 只注册带 ORM `@Mapper` 的接口，因此转换器不会被注册为 Mapper；命名仍须遵守本节。

### 3.5.2 公共配置

- 【强制】全局配置**只声明一处**（业务公共模块的 `config` 包），其余转换器统一引用，**禁止**在每个 Converter 上重复声明：

```java
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface MapStructConfig {
}
```

```java
@Mapper(config = MapStructConfig.class)
public interface UserConverter { ... }
```

- 【说明】`unmappedTargetPolicy = IGNORE` 的原因：各层字段数量不一致（Entity 有 `id` / 审计字段而 BO 没有、DTO 有展示字段而 BO 没有），设为 WARN / ERROR 会产生大量噪音。代价是**漏映射静默通过**，因此按 §3.5.5 用测试弥补。

### 3.5.3 方法命名

- 【强制】使用 **`toXxx`** 形式，以**目标类型**命名：

| 转换方向 | 命名 |
| --- | --- |
| Entity → BO | `toBO(entity)` |
| BO → Entity | `toEntity(bo)` |
| DTO → BO | `toBO(request)` |
| BO → DTO | `toDTO(bo)` |

- 【强制】同一方向存在多个源 / 目标类型时，用**特征词区分**，禁止靠重载：

```java
UserQueryBO toQueryBO(UserPageRequest request);      // 分页查询入参
UserUpsertBO toUpsertBO(UserUpsertRequest request);  // 新增/更新入参
UserResponse toResponse(UserBO bo);                  // 列表响应
UserInfoResponse toInfoResponse(UserBO bo);          // 详情响应
```

- 【强制】集合方法在单对象方法名后加 **`List`** 后缀：`toBOList` / `toDTOList`。
- 【禁止】用同名重载区分单个与集合（如 `toBO(User)` 与 `toBO(List<User>)` 并存）。

### 3.5.4 更新场景

- 【强制】对象原地更新统一为：

```java
void updateEntity(XxxBO bo, @MappingTarget Xxx entity);
```

- 【强制】`toEntity` = **新建**，`updateEntity` = **原地更新**，两者语义必须区分。
- 【强制】`updateEntity` 依赖 `nullValuePropertyMappingStrategy = IGNORE` 实现"只更新非 null 字段"。
- 【强制】修改全局 `nullValuePropertyMappingStrategy` 时必须评估对 `updateEntity` 语义的影响。

### 3.5.5 职责边界与静默风险

- 【强制】只做**字段映射**与**简单纯计算**（如由 `birthday` 计算 `age`）。
- 【禁止】在转换器中查库、写库、访问缓存、调用远程服务、执行业务规则，或把转换器当作 Service 使用。
- 【强制】跨字段映射必须用 `@Mapping(source, target)` 显式声明。
- 【强制】`expression` 中禁止写全限定类名，统一通过 `imports` 引入：

```java
// 禁止：冗长，且改包即挂
@Mapping(target = "age", expression = "java(com.example.app.convert.UserConverter.computeAge(bo.getBirthday()))")

// 正确：imports 引入
@Mapper(config = MapStructConfig.class, imports = UserConverter.class)
@Mapping(target = "age", expression = "java(computeAge(bo.getBirthday()))")
```

- 【推荐】多个方法共享同一映射时用 `@InheritConfiguration`，避免重复 `@Mapping`；双向转换可用 `@InheritInverseConfiguration`。
- 【推荐】核心链路（资金、订单、状态流转等）的关键转换，通过**单元测试**断言目标对象关键字段非 null，弥补 `unmappedTargetPolicy = IGNORE` 的静默漏映射风险（见 `testing.md`）。

---

# 四、命名规范

- 【强制】类 `UpperCamelCase`；方法 / 变量 `lowerCamelCase`；常量 `UPPER_SNAKE_CASE`；包名全小写。
- 【强制】**DTO 字段名即 JSON 字段名**，统一使用 `lowerCamelCase`；**禁止**用下划线或连字符作为对外字段名（数据库列名的下划线风格见 `database.md`）。
- 【强制】命名必须表达真实业务含义；**禁止**把 `data` / `info` / `obj` / `temp` / `result` 这类泛化命名用于长期业务变量。
- 【禁止】使用拼音命名。
- 【推荐】使用完整单词，避免无约定缩写。
- 【推荐】方法名使用业务动作：`createUser`、`submitOrder`、`cancelOrder`、`approveApplication`；避免 `handle()`、`process()`、`execute()`、`deal()`。

## 4.1 Boolean 命名

- 【推荐】Java 布尔属性用自然语义命名（`enabled`、`visible`、`active`），**不带 `is_` 前缀**。
- 【强制】数据库布尔列按 `database.md` 用 `is_xxx`。
- 【强制】数据库列与 Java 属性之间使用**显式映射**，不依赖不稳定的自动命名推导。

```java
// 数据库 is_visible → Java visible
@TableField("is_visible")
private Boolean visible;
```

- 【强制】布尔类型只用于**属性语义**（默认、可见、启用等）；**流程 / 生命周期语义**（审核、认证、激活）即使当下只有两个值，也必须建模为 `status` + 枚举，**禁止**用布尔承载流程状态。

---

# 五、事务

- 【强制】事务边界位于 Service 的业务方法上，围绕一个明确的业务原子操作。
- 【禁止】在 Controller 上添加事务注解。
- 【禁止】在数据访问层无意义添加事务注解。
- 【禁止】把整个类标记为事务。
- 【推荐】事务尽可能短；事务内不执行慢查询、HTTP 调用、文件上传、长时间等待或不可控的第三方调用。
- 【强制】注意**自调用不经过 Spring 代理**，`this.xxx()` 不会开启事务。

```java
@Transactional
public void createMatch(Long userId, Long targetUserId) {
    // 校验 → 写入 match → 更新相关状态
}
```

> 事务与缓存 / MQ / 外部系统的一致性设计见 `reliability.md`。

---

# 六、并发与数据一致性

- 【强制】任何"先查询、再修改"的核心业务都必须分析并发场景。
- 【禁止】用 Java `synchronized` 保证**数据库**数据一致性。
- 【强制】涉及库存、余额、额度、次数、状态流转等关键数据时，必须用数据库原子更新、状态守卫、唯一约束或乐观锁保证一致性。
- 【强制】业务唯一性由**数据库唯一约束**保证；**禁止**"先查后插"（并发下仍会重复）。

## 6.1 优先：带业务语义的状态守卫（CAS）

【推荐】有天然业务状态可作前置条件时，用**条件更新 + 判断影响行数**（CAS）：

```sql
UPDATE order_main
SET status = :newStatus, updated_at = CURRENT_TIMESTAMP
WHERE id = :id AND status = :expectedStatus;
-- 影响行数为 0 → 状态已被他人改变，按业务规则处理
```

【说明】状态守卫的判据**有业务语义**（调用方读代码即知为什么），比无语义的版本号更易维护，且对手写 SQL 同样适用。补偿场景的三态判定与 CAS 抢占见 `../decisions/0004-compensation-idempotency-and-race.md` 与 `reliability.md`。

## 6.2 备选：version 乐观锁

【推荐】高并发、低冲突且**没有天然状态可作守卫**的更新场景，使用 `version` 乐观锁。

- 【强制】`version` 必须**真正参与 `UPDATE` 的 `WHERE` 条件**，不能只加字段不校验。
- 【强制】影响行数为 0 时必须按业务规则处理（重试或返回冲突），**禁止**忽略返回值。

```sql
UPDATE order_main
SET status = :newStatus, version = version + 1, updated_at = CURRENT_TIMESTAMP
WHERE id = :id AND version = :version;
```

【说明】两种方式的取舍：**能用状态守卫就用状态守卫**；`version` 只在没有业务状态可作为前置条件时才引入，它带来的代价是每个写操作都要携带版本号、且读代码的人看不出"为什么被拒绝"。

---

# 七、依赖注入

- 【强制】优先使用构造器注入。
- 【禁止】在业务代码中使用字段注入。
- 【禁止】通过 `ApplicationContext.getBean()` 获取业务 Bean（基础设施 / 框架级场景除外）。
- 【推荐】Service 使用 `@RequiredArgsConstructor`。
- 【推荐】构造器依赖超过 7 个时，重新考虑该类职责是否过多。

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
}
```

---

# 八、配置管理

- 【强制】配置文件只存配置，不存业务逻辑。
- 【禁止】密码、Token、AccessKey、私钥等敏感信息提交到 Git。
- 【推荐】复杂配置使用 `@ConfigurationProperties`，避免大量 `@Value`。

---

# 九、Bean 生命周期

- 【禁止】在初始化回调中执行复杂业务逻辑。
- 【禁止】在启动阶段执行长时间、不可控的远程调用。
- 【推荐】需要应用启动后执行的任务，使用 `ApplicationReadyEvent` 等明确的生命周期机制，并保证任务幂等。
- 【推荐】生命周期回调优先用 `jakarta.annotation.PostConstruct` / `PreDestroy`，不用 `InitializingBean` / `DisposableBean`——后者会让代码不必要地耦合到 Spring。仅当类本身是 Spring 基类扩展（如 `RedisTemplate` 子类）或需要脱离容器手动触发时才用接口形式。
- 【推荐】需要容器能力（`ApplicationContext` / `BeanFactory` / `Environment`）时，优先通过**构造器或 `@Bean` 方法参数**注入；`*Aware` 只用于“由容器直接实例化、不经过我们的 `@Bean` 方法”的类（如 `BeanPostProcessor`、`BeanFactoryPostProcessor`）。

---

# 十、AOP

- 【推荐】用于横切关注点：日志、权限、审计、监控、事务。
- 【禁止】用 AOP 隐藏核心业务流程。
- 【禁止】为了少写几行代码而大量增加切面。
- 【强制】切面必须有明确作用域，避免对所有 Bean 全局生效。

---

# 十一、事件机制

- 【推荐】同进程解耦可以使用 Spring `ApplicationEvent`。
- 【禁止】把 Spring 事件当可靠消息使用（不持久化、不重投、重启即丢）。
- 【推荐】涉及跨进程、可靠投递、重试、持久化的场景使用真正的消息中间件。
- 【推荐】数据库提交后的事件使用事务事件机制，明确 `AFTER_COMMIT` 语义。

> 可靠投递的统一方案见 `reliability.md`。

---

# 十二、异步与线程池

- 【禁止】使用 `new Thread()`。
- 【禁止】在生产代码中无规划地使用默认线程池执行异步任务。
- 【强制】线程池必须**显式配置**：核心线程数、最大线程数、队列容量、拒绝策略、线程命名。
- 【强制】异步任务必须考虑：上下文传递（如 traceId）、异常处理、超时、应用关闭时的优雅收敛。
- 【说明】不要复用容器中的 `ThreadPoolTaskExecutorBuilder` 创建多个池：它是单例且方法可变，链式调用会污染后续使用者的配置。一个 Builder 只产一个池；框架内部池（投递、轮询）应显式传参、不读取 `spring.task.execution.*`。

---

# 十三、定时任务

- 【强制】定时任务必须**幂等**。
- 【强制】多实例部署时必须考虑重复执行，用分布式锁、任务分片或调度中心解决。
- 【禁止】用"sleep 一会儿"解决任务竞争。
- 【推荐】任务日志记录开始时间、结束时间、处理数量、失败数量。

---

# 十四、缓存

- 【强制】缓存不是数据源：不能把数据库必须持久化的数据只放在缓存中。
- 【强制】缓存 key 必须**集中定义**（含格式说明与 TTL），**禁止**在业务代码中散落硬编码 key 字符串。
- 【强制】必须考虑缓存击穿、穿透、雪崩。
- 【强制】删除 / 更新数据库与缓存时必须明确一致性策略。
- 【推荐】TTL 增加随机抖动，避免同一批 key 同时过期。

---

# 十五、外部调用

- 【强制】所有 HTTP 客户端必须配置连接超时、读取超时与整体调用超时；**禁止**无超时调用。
- 【强制】必须区分客户端异常、网络异常、第三方业务异常。
- 【推荐】可重试请求采用有限次数重试 + 退避策略。
- 【强制】重试前必须确认接口是否**幂等**。

---

# 十六、循环依赖

- 【强制】禁止 Bean 循环依赖。
- 【禁止】通过开启循环引用开关掩盖设计问题。
- 【推荐】通过重构职责、抽取领域服务、事件机制等方式解决。

---

# 十七、依赖管理

- 【强制】依赖版本统一由 BOM 管理，**禁止**在多个模块重复声明同一依赖的版本。
- 【禁止】为了一个简单功能引入大型框架。
- 【推荐】新增依赖必须回答三个问题：为什么需要、是否已有能力、维护成本是什么。
- 【强制】定期检查存在已知安全漏洞的依赖。

---

# 十八、安全

- 【强制】所有外部输入均视为不可信。
- 【强制】数据库访问使用参数绑定，**严禁**拼接用户输入。
- 【强制】文件上传必须限制类型、大小与存储位置。
- 【强制】对外部 URL 的调用必须防止 SSRF。
- 【强制】权限校验必须在后端完成，**不得信任**前端传递的 `userId` / `role` / `tenantId` 等身份字段——**禁止**用客户端传来的 `userId` 判断"当前登录用户"。
- 【推荐】当前用户身份统一从认证上下文获取。

---

# 十九、性能

- 【强制】循环内的数据库调用与 HTTP 调用必须重点审查。
- 【强制】大数据量查询必须明确上限、分页或流式处理策略。
- 【推荐】性能优化必须以指标或执行计划为依据；**禁止**凭感觉加缓存、加线程、加索引。

---

# 二十、评审清单

代码提交前逐条确认。**❌ 后是最典型的错误做法**；规则在前文对应章节已展开，此处只给判定。

## 20.1 结构与分层

- [ ] Controller 是否只做协议适配（❌ 直接操作数据访问层 / Redis / MQ；❌ 开事务）？
- [ ] Service 是否承担了业务职责，且不返回统一响应体（❌ 返回 `Result`）？
- [ ] 业务断言是否使用 `BizAssert`（❌ 到处 `throw new BizException`）？
- [ ] 数据访问层是否只做数据访问（❌ 编写复杂业务规则；❌ Service 中拼接 SQL）？
- [ ] 是否存在跨层调用、反向依赖或 Bean 循环依赖（❌ 用开关掩盖循环依赖）？
- [ ] 是否出现无价值的抽象层、重复实现框架已有能力？
- [ ] 是否在 DTO / Entity 上堆了业务逻辑？

## 20.2 事务与并发

- [ ] 事务边界是否在 Service 的业务方法上（❌ Controller 开事务；❌ 整个类标事务）？
- [ ] 事务内是否有远程调用、文件操作或长时间等待（❌ 在事务内做这些）？
- [ ] 是否存在 `this.xxx()` 自调用导致事务注解失效？
- [ ] 是否分析了并发更新？关键数据是否用状态守卫 / 唯一约束 / 乐观锁，且检查了影响行数（❌ 用 `synchronized` 保证数据库一致性）？
- [ ] 业务唯一性是否由数据库唯一约束保证（❌ 先查后插）？

## 20.3 运行时

- [ ] 是否使用构造器注入（❌ 字段注入；❌ 用 `getBean()` 取业务 Bean）？
- [ ] 配置是否用 `@ConfigurationProperties`？敏感信息是否远离 Git（❌ 密码 / Token / 私钥提交）？
- [ ] 异步任务是否显式配置线程池并处理上下文 / 异常 / 超时（❌ `new Thread()`；❌ 默认线程池跑生产任务）？
- [ ] 定时任务是否幂等、多实例安全（❌ 无幂等设计；❌ 用 sleep 解决竞争）？
- [ ] 缓存 key 是否集中定义？是否考虑击穿 / 穿透 / 雪崩与一致性策略（❌ key 散落硬编码）？
- [ ] 外部调用是否有超时？是否区分异常类型？重试前是否确认幂等（❌ 无超时调用）？

## 20.4 安全、性能与日志

- [ ] 是否信任了客户端传来的身份字段？是否存在 SQL 注入 / SSRF 风险？
- [ ] 文件上传是否限制类型、大小与存储位置？
- [ ] 是否存在循环内的数据库 / HTTP 调用？大数据量是否有上限或流式策略？
- [ ] 性能优化是否有指标或执行计划依据（❌ 凭感觉加缓存 / 线程 / 索引）？
- [ ] 是否在循环内打大量日志、或把敏感信息写入日志（见 `observability.md`）？
- [ ] 业务代码中是否存在 `status == 1` 之类的魔法数字？
- [ ] 依赖版本是否由 BOM 统一管理（❌ 多模块重复声明版本）？
