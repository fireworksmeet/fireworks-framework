# fireworks-id-spring-boot-starter

基于腾讯 Leaf（号段模式）的分布式 ID 生成 Starter，提供数据库号段 + 双缓存的无间断 ID 发放能力。

## 功能特性

- **号段模式**：基于 Leaf Segment 模式，内存双缓存预取号段，高并发下保证 ID 发放不中断。
- **统一工具类**：通过 `IdUtil` 静态方法便捷获取分布式 ID 或 UUID。
- **内置业务号段枚举**：`IdType` 预定义订单、会员、通用流水号三类号段 key。

## 核心 API

### `IdUtil`

| 方法 | 说明 |
| --- | --- |
| `getUUID()` | 获取 UUID（带横线） |
| `getId(String key)` | 按指定业务号段 key 获取分布式 ID（基于 Leaf SegmentService） |
| `getId(IdType idType)` | 按预定义的 `IdType` 枚举获取分布式 ID |

```java
// 获取一个分布式 ID
long orderId = IdUtil.getId("ORDER");

// 通过 IdType 枚举获取
long orderId = IdUtil.getId(IdType.ORDER);
```

### `IdType`

内置的业务号段枚举（对应数据库中 `T_LEAF_ALLOC.biz_tag`）：

| 枚举 | 对应 biz_tag | 建议起始值 |
| --- | --- | --- |
| `ORDER` | `ORDER` | 12 位（订单号） |
| `MEMBER` | `MEMBER` | 9 位（会员号） |
| `SERIAL_NUMBER` | `SERIAL_NUMBER` | 16 位（通用流水号） |

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-id-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 开启号段模式

```yaml
leaf:
  segment:
    enable: true
    url: ${spring.datasource.url}
    username: ${spring.datasource.username}
    password: ${spring.datasource.password}
```

### 3. 初始化数据库表

在数据库中执行 `resources/db/T_LEAF_ALLOC.sql`，生成 `T_LEAF_ALLOC` 表。

### 4. 为号段插入初始数据

获取 ID 前，需要为每个业务号段在数据库中插入一条记录：

```sql
insert into T_LEAF_ALLOC(biz_tag, max_id, step, description)
values ('自定义key', 首次获取的值, 一次取多少个, '为当前号段添加一个描述');

-- 如：初始值可设大一点。假设订单号 12 位（参考京东），
-- 可使用 RandomUtils.nextLong(100000000000L, 200000000000L) 取一个 12 位起始值后插入
insert into T_LEAF_ALLOC(biz_tag, max_id, step, description)
values ('leaf-segment-test', 1, 5000, 'Test leaf Segment Mode Get Id');
```

### 5. 获取 ID

```java
long id = IdUtil.getId("上面插入时自定义key");
```

## 忽略 Leaf 的刷屏日志

Leaf 号段模式采用双缓存异步加载机制，且集成了 `Perf4j` 性能监控工具，默认以 `INFO` 级别每分钟输出耗时日志。生产环境可通过如下方式关掉：

**Logback（Spring Boot 默认）**，在 `logback-spring.xml` 中添加：

```xml
<!-- 忽略 leaf 的日志 -->
<logger name="com.tencent.devops.leaf.segment.SegmentIDGenImpl" level="WARN" />
<logger name="org.perf4j.TimingLogger" level="WARN" />
```

**或直接修改 Spring Boot 配置文件：**

```yaml
logging:
  level:
    com.tencent.devops.leaf.segment.SegmentIDGenImpl: WARN
    org.perf4j.TimingLogger: WARN
```
