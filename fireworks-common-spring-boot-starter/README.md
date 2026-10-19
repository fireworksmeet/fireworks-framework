# fireworks-common-spring-boot-starter

公共基础能力 Starter，提供**通用工具类**、**加密算法**、**数据脱敏**、**Jackson 全局配置**、**日志统一**、**AOP 元数据基础设施**与**状态码约定**。被其他所有模块依赖。

## 功能特性

- **工具类**：`JsonUtil`、`SpringContextHolder`、`HashUtil`、`PasswordUtil`、`StringUtil`、`SpelUtil`、`Base64Util`、`DateUtil`、`InstantUtil`、`ZoneUtil`、`TaskExecutorUtil`、`ValidationUtil`。
- **加密算法**：AES-GCM、RSA、国密 SM2 / SM4（`util/crypto` 包）。
- **数据脱敏**：字段级 `@Sensitive` + Jackson `SensitiveModule` 自动脱敏；算法集中在 `SensitiveUtil`，非序列化场景（日志、导出）可直接调用。
- **Jackson 全局配置**：统一时间格式、BigDecimal 字符串化、未知字段忽略、脱敏模块等。
- **日志统一**：`LoggingDefaultsEnvironmentPostProcessor` 以最低优先级注入统一日志格式（预留 traceId / spanId 关联段位置，内容由追踪实现注入），`logging.pattern.*` 等标准开关全部有效。
- **AOP 元数据基础设施**：`AbstractAnnotationMetadataSource` + `MetadataSourcePointcut`，被锁、操作日志等复用。
- **状态码约定**：`ExceptionStatus` 接口 + `CommonExceptionStatus` 通用状态码。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-common-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. JSON 工具

基于容器中的 `ObjectMapper`（保证全局序列化一致）：

```java
String json = JsonUtil.stringify(user);                 // 序列化
User user = JsonUtil.deserialize(json, User.class);     // 反序列化
List<User> list = JsonUtil.toList(json, User.class);    // 转 List
Map<String, Object> map = JsonUtil.toMap(json);         // 转 Map
User copy = JsonUtil.convertValue(dto, User.class);     // 对象转换
```

### 3. Spring 上下文工具

```java
// Bean 获取（空安全，未初始化/找不到时返回 null，不抛异常）
UserService svc = SpringContextHolder.getBean(UserService.class);
Map<String, UserService> all = SpringContextHolder.getBeansOfType(UserService.class);

// 配置读取
String name = SpringContextHolder.getProperty("spring.application.name", "unknown");

// 动态注册 Bean
SpringContextHolder.registerBean(MyService.class);
SpringContextHolder.registerBean("myService", MyService.class, null, constructorArg);

// 发布事件
SpringContextHolder.publishEvent(new MyEvent());
```

### 4. 数据脱敏

在实体字段上标注 `@Sensitive`，序列化时自动脱敏：

```java
public class UserVO {
    @Sensitive(SensitiveType.PHONE)    // 13812345678 → 138****5678
    private String phone;

    @Sensitive(SensitiveType.ID_NUMBER) // 110101199001011234 → 110***********1234
    private String idNumber;

    @Sensitive(SensitiveType.NAME)     // 张三丰 → 张**
    private String name;
}
```

内置脱敏类型（`SensitiveType`）：`NAME`、`PHONE`、`ID_NUMBER`、`BANK_CARD`、`EMAIL`、`FIXED_PHONE`、`ADDRESS`、`IP_ADDRESS`、`PASSPORT`、`UNIFIED_CREDIT_CODE`、`CAR_LICENSE`、`PASSWORD`、`USERNAME`、`CUSTOM_HIDE_ALL`。`value` 为必填项，没有默认值。

算法与声明分离：`SensitiveType` 只做「声明 → 算法」映射，算法本体在 `SensitiveUtil`。日志、导出等非序列化场景可直接调用工具方法：

```java
log.info("send sms, phone={}", SensitiveUtil.phone(phone));
String masked = SensitiveType.PHONE.process(phone);   // 等价写法
```

### 5. 加密算法

```java
// AES-GCM
String encrypted = AesUtil.encrypt("明文", secretKey);

// RSA（生成密钥对或使用既有公私钥）
RsaKeyPair pair = RsaUtil.generateKeyPair();
String cipher = RsaUtil.encrypt("明文", pair.getPublicKey());

// 国密 SM2 / SM4
Sm2KeyPair sm2Pair = Sm2Util.generateKeyPair();
String sm4 = Sm4Util.encrypt("明文", key);
```

### 6. 密码散列（BCrypt）

```java
String hash = PasswordUtil.hash("rawPassword");        // 加盐 BCrypt 哈希
boolean ok = PasswordUtil.matches("rawPassword", hash); // 校验
```

### 7. 线程池与上下文传递

`TaskExecutorUtil` 不自定义参数模型，直接复用 Spring 官方的 `ThreadPoolTaskExecutorBuilder`。
Builder 从容器注入：它是 Boot 依据 `spring.task.execution.*`、容器中的 `ThreadPoolTaskExecutorCustomizer`
以及容器中唯一的 `TaskDecorator` 预先配置好的 Bean，因此异步线程可继承提交线程的上下文。
该 `TaskDecorator` 由 `fireworks-tracing` 注册为 `ContextPropagatingTaskDecorator`（本模块不做上下文传播，
业务未引入 tracing 时线程池照常可用，只是没有可传播的上下文）：

```java
@Bean
public ThreadPoolTaskExecutor orderExecutor(ThreadPoolTaskExecutorBuilder builder) {
    return TaskExecutorUtil.build(
            builder.corePoolSize(8)
                    .maxPoolSize(16)
                    .keepAlive(Duration.ofSeconds(60))
                    .queueCapacity(200)          // 必须收敛，否则 maxPoolSize 形同虚设
                    .threadNamePrefix("order-async-")
                    .awaitTermination(true)
                    .awaitTerminationPeriod(Duration.ofSeconds(30)),
            new ThreadPoolExecutor.CallerRunsPolicy());   // 自动传递 traceId
}
```

`TaskExecutorUtil` 只补 Builder 的两处短板：拒绝策略没有配置入口、产出实例未 `initialize()`。
Builder 一律从容器注入，不做"容器里没有就自己 new 一个"的降级 —— 那会让两条路径的默认值不一致
（`corePoolSize` 会变成 Spring 默认的 `1` 而不是 Boot 的 `8`）。

### 8. 校验工具

```java
boolean isMobile = ValidationUtil.isMobile("13812345678");
boolean isEmail = ValidationUtil.isEmail("a@b.com");
```

### 8.1 时间工具（时区感知）

框架按**时间语义**拆分为三个工具类，避免混用 `LocalDateTime`（墙钟时间、无时区）造成跨时区序列化错乱：

| 工具类 | 适用场景 | 典型类型 |
| --- | --- | --- |
| `DateUtil` | 纯本地时间展示 / 计算 | `LocalDateTime` / `LocalDate` / `LocalTime` |
| `InstantUtil` | 绝对时间点（跨时区一致），落库 / 传输建议使用 | `Instant`，`now` / 转时间戳 / 指数退避 |
| `ZoneUtil` | 时区换算、偏移量计算 | `ZoneId` / `ZoneOffset` |

```java
// 业务字段/数据库建议用 Instant（绝对时间点），序列化后含时区偏移，跨服务/跨时区一致
Instant createdAt = InstantUtil.now();

// 需要按本地时区展示时，再转换为 LocalDateTime
LocalDateTime local = ZoneUtil.toLocalDateTime(createdAt, ZoneId.of("Asia/Shanghai"));

// 指数退避（重试场景）：第 n 次重试的延迟 / 下次重试时间
Instant next = InstantUtil.calculateNextRetryTime(attempt, maxRetries, initialDelayMs, maxDelayMs, jitter, Instant.now());
```

### 9. Jackson 全局配置

`CommonAutoConfiguration` 自动注册一个 `Jackson2ObjectMapperBuilderCustomizer`（Spring Boot 会收集容器中所有该类型的 Bean 并统一应用），对容器中的 `ObjectMapper` 生效：

**基础特性**
- 禁用时间戳输出（`WRITE_DATES_AS_TIMESTAMPS`）。
- 反序列化忽略未知字段；空对象序列化不抛异常。
- 只序列化非空字段（`NON_NULL`）。
- 允许单引号、非引号属性名、未转义控制字符、反斜杠转义任意字符等宽松解析。

**时间类型（重要）**
- **不再手动配置任何时间序列化器 / 时区**，统一交由 Jackson `JavaTimeModule` 的**默认实现**按各自 ISO 格式序列化/反序列化。
- `LocalDateTime` → `2026-08-18T12:30:00`（ISO，无时区）；`Instant` → `2026-08-18T04:30:00Z`（UTC，带 Z）；`OffsetDateTime`/`ZonedDateTime` 含时区偏移。
- **不设置全局 `TimeZone`**：避免人为给没有时区的时间类型（如 `LocalDateTime`）附加全局时区语义，防止跨时区序列化错乱。

**数值与模块**
- `BigDecimal` / `BigInteger` 以普通字符串输出（防科学计数法与 JS 精度丢失）。
- 自动注册脱敏模块 `SensitiveModule`。

### 10. 日志统一

框架**不提供 `logback-spring.xml`**，而是通过 `LoggingDefaultsEnvironmentPostProcessor` 以**最低优先级**注入一组默认日志格式。业务**零配置**即可获得统一格式：

```text
2026-10-14T10:20:30.123+08:00  INFO 12345 --- [8f3c1a2b9d4e6f70,3a1b2c4d5e6f7081] [http-nio-8080-exec-1] c.y.demo.OrderController : create order success
```

注入的默认值（全部可被业务覆盖）：

| 属性 | 作用 |
| --- | --- |
| `logging.pattern.console` | 控制台格式 |
| `logging.pattern.file` | 文件格式（去掉颜色） |

**只预留关联段占位，不注入内容**：两个格式里都有 `${logging.pattern.correlation:}` 占位（缺省渲染为空），
但本模块**既不设置 `logging.pattern.correlation`，也不引用任何 MDC 键**——键名取决于追踪实现：
Micrometer Tracing 用 `traceId` / `spanId`，Java Agent 探针（OTel 等）多为 `trace_id` / `span_id`。
写死任何一套，另一套实现下的工程就会把关联段稳定渲染成空值且不报错。

| 追踪实现 | MDC 键名 | 谁注入关联段 |
| --- | --- | --- |
| 依赖模式（`fireworks-tracing`，由 `fireworks-webmvc` 默认引入） | `traceId` / `spanId` | 该模块自动注入 |
| Java Agent 探针（OTel 等） | `trace_id` / `span_id` | 业务声明 `logging.pattern.correlation` |

走 `fireworks-webmvc` 时，上面示例中的 `[8f3c1a…,3a1b2c…]` 由 `fireworks-tracing` 填入。

**标准开关全部有效**，按需覆盖即可：

```yaml
logging:
  pattern:
    console: '%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n'   # 完全自定义
    correlation: '[%X{trace_id:-},%X{span_id:-}]'               # 换追踪实现时对齐关联段键名
  level:
    com.yzm.demo: DEBUG
```

**落盘（默认关闭）**：框架不注入 `logging.file.*`，默认只输出到控制台（与 Boot 默认一致；容器化部署应由平台采集 stdout / stderr）。需要落盘时自行开启：

```yaml
logging:
  file:
    name: /logs/app.log
  logback:
    rollingpolicy:
      max-file-size: 200MB
      max-history: 90
      file-name-pattern: /logs/app-%d{yyyy-MM-dd-HH}-%i.gz
```

**说明**

- 仅在 classpath 存在 Logback 时注入；使用 Log4j2 等实现的业务不受影响，自行配置即可。
- 默认格式依赖 Boot 默认配置注册的 `clr` / `wEx` / `correlationId` 转换词；关联段里的 MDC 键名由追踪实现决定（依赖模式为 `traceId` / `spanId`，Agent 模式多为 `trace_id` / `span_id`），本模块刻意不硬编码键名。
- **`logging.pattern.*` 里的占位符必须用 Spring 语法 `${key:默认值}`，不要用 Logback 的 `${key:-默认值}`**：该值先由 Spring 解析（Spring 的默认值分隔符是 `:`），`:-` 会被解析成"默认值 = `-默认值`"，导致日志里凭空多出 `-`。框架默认格式已按 Spring 语法编写。
- 需要 Boot 默认给不了的 appender 拓扑（JSON 输出、访问日志分流等）时，业务自己的 `logback-spring.xml` 应**基于 Boot 官方片段做增量**，不要从零重写：

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/base.xml"/>
    <!-- 在此追加自己的 appender -->
</configuration>
```

## 核心类速览

| 类 | 说明 |
| --- | --- |
| `JsonUtil` | JSON 序列化 / 反序列化 / 转换 |
| `SpringContextHolder` | Spring 上下文持有、Bean 获取 / 注册、配置读取、事件发布 |
| `HashUtil` | MD5 / SHA / Hmac 等哈希工具 |
| `PasswordUtil` | BCrypt 密码加盐哈希与校验 |
| `AesUtil` / `RsaUtil` / `Sm2Util` / `Sm4Util` | 对称 / 非对称 / 国密加解密 |
| `StringUtil` / `SpelUtil` / `Base64Util` | 字符串、SpEL、Base64 工具 |
| `DateUtil` / `InstantUtil` / `ZoneUtil` | 时间工具：本地时间格式、绝对时间点、时区换算（含指数退避） |
| `TaskExecutorUtil` | 线程池工厂：基于容器注入的 Builder，补齐拒绝策略并初始化 |
| `ValidationUtil` | 常用格式校验 |
| `@Sensitive` / `SensitiveType` / `SensitiveModule` | 数据脱敏 |
| `LoggingDefaultsEnvironmentPostProcessor` | 日志默认格式注入（最低优先级；`logging.pattern.*` 等标准开关仍可覆盖） |
| `AbstractAnnotationMetadataSource` / `MetadataSourcePointcut` | AOP 注解元数据基础设施 |
| `ExceptionStatus` / `CommonExceptionStatus` | 状态码接口 / 通用状态码 |

## 注意事项

- `SpringContextHolder` 采用**空安全**设计，容器未初始化时返回 `null` / 默认值而非抛异常。
- `Result` 等响应类依赖本模块的 `CommonExceptionStatus` 与 `SpringContextHolder`。
