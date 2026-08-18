# fireworks-common-spring-boot-starter

公共基础能力 Starter，提供**通用工具类**、**加密算法**、**数据脱敏**、**Jackson 全局配置**、**MDC 上下文传递**、**AOP 元数据基础设施**与**状态码约定**。被其他所有模块依赖。

## 功能特性

- **工具类**：`JsonUtil`、`SpringContextHolder`、`HashUtil`、`PasswordUtil`、`StringUtil`、`SpelUtil`、`Base64Util`、`DateUtil`、`InstantUtil`、`ZoneUtil`、`ThreadPoolUtil`、`ValidationUtil`。
- **加密算法**：AES-GCM、RSA、国密 SM2 / SM4（`util/crypto` 包）。
- **数据脱敏**：`@Sensitive` 注解 + Jackson `SensitiveModule`，支持手机号、身份证、银行卡、邮箱等十余种脱敏规则。
- **Jackson 全局配置**：统一时间格式、BigDecimal 字符串化、未知字段忽略、脱敏模块等。
- **MDC 上下文传递**：`MdcTaskDecorator` 在异步线程池中传递 MDC / Tracing 上下文（traceId、span）。
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

内置脱敏类型（`SensitiveType`）：`NAME`、`PHONE`、`ID_NUMBER`、`BANK_CARD`、`EMAIL`、`FIXED_PHONE`、`ADDRESS`、`IP_ADDRESS`、`PASSPORT`、`UNIFIED_CREDIT_CODE`、`CAR_LICENSE`、`PASSWORD`、`USERNAME`、`CUSTOM_HIDE_ALL`。

> `@Sensitive` 还可标注在方法参数上（配合 AOP 日志脱敏）。

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

### 7. 线程池与 MDC 传递

`ThreadPoolUtil` 提供便捷的 `ThreadPoolTaskExecutor` 工厂；`CommonAutoConfiguration` 默认注册一个 `MdcTaskDecorator`（`TaskDecorator`），
将其传给线程池即可在异步线程中传递 MDC / Tracing 上下文：

```java
ThreadPoolTaskExecutor executor = ThreadPoolUtil.createThreadPoolTaskExecutor(
        4, 8, 60, 200, "my-pool-", new ThreadPoolExecutor.CallerRunsPolicy(),
        60, new MdcTaskDecorator()   // 自动传递 traceId
);
```

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
| `ThreadPoolUtil` | 线程池工厂 |
| `ValidationUtil` | 常用格式校验 |
| `@Sensitive` / `SensitiveType` / `SensitiveModule` | 数据脱敏 |
| `MdcTaskDecorator` | MDC / Tracing 上下文传递装饰器 |
| `AbstractAnnotationMetadataSource` / `MetadataSourcePointcut` | AOP 注解元数据基础设施 |
| `ExceptionStatus` / `CommonExceptionStatus` | 状态码接口 / 通用状态码 |

## 注意事项

- `SpringContextHolder` 采用**空安全**设计，容器未初始化时返回 `null` / 默认值而非抛异常。
- `Result` 等响应类依赖本模块的 `CommonExceptionStatus` 与 `SpringContextHolder`。
