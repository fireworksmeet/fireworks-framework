# fireworks-redis-spring-boot-starter

基于 Spring Data Redis + Redisson 的 Redis 增强 Starter，提供**多数据源**、**分布式锁**、**JSON 序列化模板**等能力。

## 功能特性

- **多数据源**：一套 Redis 配置节点下管理多个 Redis 实例，通过 `RedisUtil.on("数据源")` 无状态切换，天然支持异步/多线程。
- **分布式锁**：提供 `@DistributedLock` 注解（AOP）与 `LockService`（编程式）两种加锁方式，基于 Redisson，支持多数据源指定。
- **JSON 序列化**：`JsonRedisTemplate` 使用容器中的 `ObjectMapper` 做值序列化，保证全局序列化一致。
- **批量操作 / Pipeline**：提供符合序列化约定的 Pipeline 用法示例。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-redis-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 单数据源

使用 Spring Boot 原生 Redis 配置即可：

```yaml
spring:
  data:
    redis:
      host: 192.168.145.128
      port: 6379
      timeout: 3000
```

### 3. 多数据源

需要同时使用多个 Redis 实例时，启用 `multi`：

```yaml
spring:
  data:
    redis:
      multi:
        enabled: true     # 开启多数据源
        primary: aaa      # 默认数据源名称，未显式指定时使用
        datasource:
          aaa:            # 自定义数据源名称，值为 RedisProperties 相关配置
            host: 192.168.145.128
            port: 6379
            timeout: 3000
          bbb:
            host: 192.168.145.129
            port: 6379
            timeout: 3000
```

### 4. 切换数据源

通过 `RedisUtil.on("数据源名称")` 显式切换，返回绑定到该数据源的操作上下文；不调用 `on()` 则默认使用 primary 数据源。

```java
// 默认（primary）数据源
RedisUtil.set("key:1", value);

// 切换到 bbb 数据源
RedisUtil.on("bbb").set("key:1", value);

// 异步/多线程场景同样安全（无 ThreadLocal 状态）
executor.submit(() -> RedisUtil.on("bbb").set("key:1", value));
```

> `on()` 返回的是轻量的 `RedisContext` 视图，不持有全局状态，可安全跨线程使用。

## 分布式锁

### 1. 注解方式（`@DistributedLock`）

在方法上添加注解即可自动加锁 / 释放锁：

```java
// 默认：key = 方法全限定名，使用 primary 数据源
@DistributedLock
public void processOrder(Long orderId) { ... }

// 指定 key 前缀 + SpEL 动态 key
@DistributedLock(prefixKey = "order", key = "#orderId")
public void processOrder(Long orderId) { ... }

// 多数据源下，指定锁所在的 Redis 数据源（确保不同服务锁在同一实例）
@DistributedLock(prefixKey = "order", key = "#orderId", datasource = "order")
public void processOrder(Long orderId) { ... }
```

参数说明：

| 属性 | 说明 | 默认值 |
| --- | --- | --- |
| `prefixKey` | key 前缀，缺省为方法全限定名 | 方法全限定名 |
| `key` | SpEL 表达式（基于方法参数上下文），最终 key = `prefixKey:值`。可用参数名（`#orderId`）、位置（`#args[0]`、`#p0`）等 | 空 |
| `waitTime` | 等待锁的最长时间，期间获取不到则报错 | `60` |
| `unit` | 等待时间单位 | `SECONDS` |
| `datasource` | 锁所在的 Redis 数据源名称，留空用 primary | 空 |

### 2. 编程方式（`LockService`）

```java
@Autowired
private LockService lockService;

// 使用 primary 数据源（默认）
lockService.executeWithLock("order:123", () -> doSomething());

// 切换到 order 数据源
lockService.on("order").executeWithLock("order:123", () -> doSomething());

// 仅尝试获取锁（不抛异常）
boolean locked = lockService.tryLock("order:123", 5, TimeUnit.SECONDS);

// 获取锁，失败抛 LockAcquisitionException
lockService.lockOrThrow("order:123", 5, TimeUnit.SECONDS);
```

## Pipeline 使用注意事项

为了保证全局都能正常使用 `ObjectMapper` 序列化，使用 `executePipelined` 时建议**不要手动调用 `getBytes()`** 序列化值，而是统一使用值序列化器：

```java
JsonRedisTemplate redisTemplate = RedisUtil.getJsonRedisTemplate();
String key = "key:111";
String value = "value";
Jackson2JsonRedisSerializer<Object> valueSerializer = redisTemplate.getValueSerializer();

redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    byte[] keyBytes = key.getBytes();                 // key 推荐 getBytes
    byte[] serialize = valueSerializer.serialize(value); // value 必须用值序列化器
    connection.stringCommands().set(keyBytes, serialize);
    return null;
});
```

> 注意：`executePipelined(RedisCallback, resultSerializer)` 的重载中，第二个参数 `resultSerializer` 用于**返回值**反序列化，不要与值的序列化器混淆。
> 对 Hash/ZSet 而言，`field` 算 key，Hash 的 value 是最后一个；ZSet 的 value 是中间那个，最后一个是 score。

## JFR 记录 Redis 命令

可通过配置在 `JFR` 中记录 Redis 命令的耗时情况：

```yaml
spring:
  data:
    redis:
      jfr:
        enabled: true
        event-emit-interval: 10s   # 每 10 秒内的命令做一次统计，默认 10s
```
