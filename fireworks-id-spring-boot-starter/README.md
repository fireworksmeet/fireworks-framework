# fireworks-id-spring-boot-starter

基于 **CosId** 的分布式 ID 生成 Starter：统一 API（`IdUtil`）+ 模式互斥校验 + 建表脚本 + 可直接复制的配置模板。

> **配置 100% 使用 CosId 原生属性**——本模块**不定义任何 `fireworks.id.*` 配置项**。
> 号段模式配 `cosid.segment.*`，雪花模式配 `cosid.snowflake.*` + `cosid.machine.*`，
> 属性语义与 CosId 官方文档**完全一致**，你从 CosId 文档里学到的知识在这里同样适用。
> 框架只提供：**统一 API、建表脚本、配置模板**，外加一条最小的启动校验——两种模式不能同时开启。

## 版本线（务必对齐）

| CosId | 对应 Spring Boot | 本项目 |
| --- | --- | --- |
| 1.x | Spring Boot 2.x / Java 8 | ❌ |
| **2.x（当前 2.15.2）** | **Spring Boot 3.x / Java 17** | ✅ 使用 |
| 3.x | Spring Boot 4.x | ❌ |

版本统一由 `fireworks-dependencies` 导入的 `cosid-bom` 管理，**不要在业务模块里写版本号**。

## 选哪种模式

| 维度 | 号段模式 `segment` | 雪花模式 `snowflake` |
| --- | --- | --- |
| 开关 | `cosid.segment.enabled: true` | `cosid.snowflake.enabled: true` |
| ID 形态 | 短，可精确控制起点与位数 | 由位分配决定，默认约 19 位十进制 |
| 有序性 | 单实例单调；**全局仅趋势递增** | **时间有序**（单实例严格递增） |
| 数据库往返 | 每 `step` 一次 | 无 |
| 起始值可控 | ✅ `offset` 可精确衔接历史号 | ❌ 与历史号无衔接关系 |
| 前置依赖 | 号段表（`script/db/cosid.sql`） | **机器号分发器** |
| 典型用途 | 订单号 / 会员号 / 流水号等**业务编号** | 主键 / 链路 ID / 消息 ID 等**技术 ID** |

> 两者**互斥**：同时开启会直接启动失败（见「模式互斥校验」）。两者生成的是**不同的号源**，
> 同一业务编号不要中途换模式（会更号、并破坏位数契约）。

## 配置模板（复制即用）

### 模板 1：号段模式（PostgreSQL，当前生产用法）

建表：执行 `script/db/cosid.sql`（在**模块根目录**下，随源码交付、**不进 jar**）。

> ⚠️ **表名 `cosid` 沿用 CosId 上游默认值，不要改**：CosId 内置 SQL、后续版本与监控工具都可能假定该表名。

> 脚本放在模块根的 `script/db/`：它只服务**人工部署**（本项目没有 Flyway/Liquibase，CosId 的自动建表也已显式关闭），
> 没有程序化消费者——因此不放进 `src/main/resources`，避免把 DDL 混进代码资源目录、打进 jar、占用 classpath 命名空间。

**关于 `cosid` 表的两个事实**：

1. **号段行不需要手工 INSERT**：首次取号时 CosId 会按 `cosid.segment.provider.<name>.offset` 自动创建该行
   （`enable-auto-init-id-segment: true`），脚本只负责建表。
2. **表里出现 `{namespace}.__share__SegmentId` 这样的行是正常的**：CosId 的**共享生成器**默认开启
   （`cosid.segment.share.enabled` 默认 `true`），首次取号时同样会建行。它对应 `IdUtil.getShareId()`
   ——无业务标识的技术 ID（traceId / 消息 ID 等）；**业务编号不走它**。若确认不用（既不调 `getShareId()`、
   也不要那段号），可以关掉：

```yaml
cosid:
  segment:
    share:
      enabled: false
```

关闭后 `getShareId()` 会 fail-fast 并提示该开关，不会静默降级。

```yaml
cosid:
  namespace: ${spring.application.name}   # ⚠️ 必须稳定：改它等于换命名空间，号段会从 offset 重新开始（CosId 默认值是 cosid）
  segment:
    enabled: true                         # ★ 号段模式开关（与 cosid.snowflake.enabled 互斥）
    mode: segment                         # 号段模式内部实现；量级上来后可切 chain（见下）
    distributor:
      type: jdbc
      jdbc:
        # 建表交给 script/db/cosid.sql；CosId 内置 DDL 是 MySQL 方言
        enable-auto-init-cosid-table: false
        # 号段行由 CosId 按 provider.<name>.offset 自动初始化（SQL 覆盖为 PG 版本）
        enable-auto-init-id-segment: true
        init-id-segment-sql: >-
          insert into cosid (name, last_max_id, last_fetch_time)
          values (?, ?, extract(epoch from now())::bigint)
          on conflict (name) do nothing
        # ⚠️ 必填覆盖项：CosId 默认用 MySQL 的 unix_timestamp()，PG 下取号会直接报错
        increment-max-id-sql: >-
          update cosid set last_max_id = last_max_id + ?,
          last_fetch_time = extract(epoch from now())::bigint where name = ?
    provider:
      # offset 必须【大于】迁移前 Leaf 表的 max_id（推荐 max_id + 1）：宁可跳一个号，也绝不能重号
      ORDER:
        offset: 131949450911              # 原 Leaf max_id = 131949450910
        step: 5000
      MEMBER:
        offset: 908536683                 # 原 Leaf max_id = 908536682
        step: 5000
      SERIAL_NUMBER:
        offset: 1544986154203             # 原 Leaf max_id = 1544986154202
        step: 10000
```

**为什么必须覆盖 SQL**：CosId 的 JDBC 分发器内置 SQL 是 MySQL 方言，直接用于 PostgreSQL 会失败：

| CosId 默认 SQL | PG 下的问题 |
| --- | --- |
| `bigint unsigned` | PG 无 `unsigned` |
| `engine = InnoDB` | MySQL 专有 |
| 列内 `comment '...'` | PG 不支持 |
| `insert ... value (?, ?, unix_timestamp())` | PG 无 `unix_timestamp()` |
| `update ... last_fetch_time = unix_timestamp()` | 同上（**取号路径每次都会执行**） |

#### 起始值与位数（`offset` 怎么定）

号段模式的**位数完全由 `offset` 决定**：`offset` 取 `10^(n-1)` 时，在 ID 涨到 `10^n` 之前恒为 n 位。
下表"常见位数"是行业经验值（**非标准**），"建议 offset"就是对应的起点：

| 用途 | 常见位数（经验值） | 建议 `offset` | 说明 |
| --- | --- | --- | --- |
| 会员号 / 短编号 | 8 ~ 10 位 | `10000000`（8 位） | 太短容易与手机号段、其它编号混淆 |
| 通用流水号 / 消息 ID | 13 ~ 16 位 | `1000000000000`（13 位） | 取号频繁、位数无特殊要求时用这一档 |
| 订单号 | 16 ~ 19 位 | `1000000000000000`（16 位） | 电商普遍 16~19 位纯数字；**19 位已顶到 `long` 上限**（`Long.MAX_VALUE` = 9223372036854775807，19 位） |
| 支付 / 交易单号 | 20 ~ 28 位 | **号段做不到** | 支付宝交易号 28 位（前 8 位是日期）、微信支付交易单号 28 位（如 `4200001832202306182824792523`）——那是平台自研编码：**日期 + 随机段**，长度超出 `long`；要这类形态请自己拼字符串或用 `date-prefix` 转换器，不要做成纯递增 |
| 内部主键 | 不需要号段 | — | 用雪花模式（64 位 long、8 字节、时间有序） |
| 短链码 / 取货码 | 6 ~ 8 位 | **不建议用号段** | 空间太小必须查重，用"随机 + 唯一索引"更合适 |

另外三条经验：

- **不要从 0 / 1 开始**：位数不确定，且极易与历史数据或其它系统的编号混淆
- **迁移场景**：`offset` 必须**大于**历史 `max_id`（宁可跳号，绝不重号）
- **`step` 与抖动**：`step` 越大数据库往返越少，但多实例之间的乱序越明显（推荐 1000 ~ 10000）

**可选：切换到号段链（SegmentChainId）**——号段耗尽时的取号抖动更小（预取安全距离），代价是 2 个后台线程：

```yaml
cosid:
  segment:
    mode: chain
    chain:
      safe-distance: 5
      prefetch-worker:
        core-pool-size: 2
        prefetch-period: 1s
```

### 模板 2：雪花模式 + Redis 机器号（推荐）

雪花模式**不需要建表**，但需要机器号（唯一性根基）。Redis 方案由 Redis 统一分配，无需人工干预：

```yaml
cosid:
  namespace: ${spring.application.name}
  snowflake:
    enabled: true                         # ★ 雪花模式开关（与 cosid.segment.enabled 互斥）
    provider:
      ORDER:                              # 业务标识：IdUtil.getId("ORDER")
        timestamp-unit: millisecond       # 默认位分配：41 位毫秒 + 10 位机器 + 12 位序列（≈19 位十进制）
  machine:
    enabled: true                         # ★ 雪花模式必需
    distributor:
      type: redis                         # 由 Redis 统一分配机器号（推荐）
                                          # 不写则 CosId 默认 manual，需自行给每个实例配唯一的 machine-id
```

`cosid-spring-redis` **已随本 starter 引入**，使用方无需再加依赖；
前提是应用里有 `StringRedisTemplate`（引入 `fireworks-redis-spring-boot-starter` 即可）。

### 模板 3：雪花模式 + K8s StatefulSet 机器号

无需额外依赖——机器号取 Pod 主机名末尾的序号（StatefulSet 的 Pod 名形如 `app-0` / `app-1`）：

```yaml
cosid:
  namespace: ${spring.application.name}
  snowflake:
    enabled: true
    provider:
      ORDER:
        timestamp-unit: millisecond
  machine:
    enabled: true
    distributor:
      type: stateful_set                 # 仅适用于 K8s StatefulSet（Pod 名必须有稳定序号）
    state-storage:
      local:
        state-location: /var/cosid/state  # 可选：容器工作目录只读时，把状态文件指到可写卷（用于冲突检测）
```

### 关闭 ID 能力

两个开关都不写（或都写 `false`）即可：不会装配任何生成器，`IdUtil` 调用时 fail-fast 抛出明确异常。
框架对"两者都不开"不做任何干预。

## 模式互斥校验（框架唯一的启动期逻辑）

`cosid.segment.enabled` 与 `cosid.snowflake.enabled` **不能同时为 `true`**：

| 触发条件 | 行为 | 为什么 |
| --- | --- | --- |
| 两者都是 `true` | **启动失败** | CosId 允许二者并存，但共享生成器 `__share__` 只有一个生效，行为不可预期——属于"配置与预期不一致"的静默故障，故在启动期直接拦下 |
| 只开一个 / 都不开 / 都 `false` | 不干预 | 框架不校验其他任何东西，也不改写任何属性 |

报错报文是自解释的：

```
两种 ID 模式互斥，不能同时开启：cosid.segment.enabled 与 cosid.snowflake.enabled 都是 true。
CosId 允许二者并存，但共享生成器（__share__）只会有一个生效，行为不可预期，故本 starter 直接拒绝启动。
请按 README 的配置模板二选一：
  · 号段模式：保留 cosid.segment.enabled=true（并移除 cosid.snowflake.enabled）
  · 雪花模式：保留 cosid.snowflake.enabled=true（并移除 cosid.segment.enabled）
```

> **其他配置错误一律交给 CosId**：机器号缺失或重复、分发器适配包漏引、号段表不存在、位分配越界……
> CosId 会在启动或取号时自行抛错，本模块不重复校验——避免同一处配置出现两套报错规则。

## 核心 API

```java
long orderId = IdUtil.getId("ORDER");              // 十进制 ID（业务编号：需先声明标识）
String no    = IdUtil.getIdAsString("ORDER");      // 字符串形态：默认 radix62 定长 11 位（十进制 102 → "0000000001e"）
long serial  = IdUtil.getId("SERIAL_NUMBER");      // 业务标识
long traceId = IdUtil.getShareId();                // 无业务标识：技术 ID（共享生成器，见下）
String trcNo = IdUtil.getShareIdAsString();        // 同上，字符串形态（默认 radix62 定长 11 位）
String uuid  = IdUtil.getUUID();                   // UUID v4：32 位紧凑格式（无连字符，随机无序）
```

**业务标识是字符串，由使用方自己定义**（枚举 / 常量类皆可，值需与 CosId 的 provider 配置名一致）——
框架**不提供**任何业务枚举，`IdType` 这类东西属于业务侧，放进框架就等于"加一个业务编号域要发框架版本"。

`IdUtil` 对两种模式**行为一致**（CosId 的号段与雪花生成器都注册进同一个 `IdGeneratorProvider`），
所以切模式**业务代码零改动**——但 ID 特性会变，见下。

**ID 特性**（按模式区分，务必理解）：

| 特性 | 号段模式 | 雪花模式 |
| --- | --- | --- |
| 单实例单调 | ✅ 每次取号一定大于上次 | ✅ |
| 全局有序 | ❌ 仅趋势递增（多实例各持号段，同一时刻会乱序；乱序程度由 `step` 与集群规模决定，**step 越小越接近全局递增**） | ✅ **时间有序**（按时间戳近似全局递增） |
| 连续性 | ❌ 实例重启、号段未用完即被丢弃都会跳号 | ❌ 不保证连续 |
| 位数 | 由 `offset` 决定 | 由位分配决定（默认 19 位） |

> 两种模式都**不要把 ID 当作「连续编号」使用**；确需连号的场景请另建数据库序列。

**失败语义**（与旧 Leaf 集成的关键差别）：旧实现的 `SegmentService` 取不到 Bean 时会**静默返回 `-1`**，
而 `-1` 会被写进订单号、会员号等业务数据。现在一律 fail-fast：

```
未声明业务标识 [ORDER]：号段模式请在 cosid.segment.provider 下声明，雪花模式请在 cosid.snowflake.provider 下声明（当前已注册：MEMBER, SERIAL_NUMBER）
```

### 技术 ID：用 `getShareId()`

技术性标识（traceId、消息 ID、幂等键、临时 token）不关心位数与业务归属，**不必声明业务标识**——
直接读 CosId 的**共享生成器**（`__share__`）：

```java
long   traceId = IdUtil.getShareId();           // 十进制
String traceNo = IdUtil.getShareIdAsString();   // 字符串形态：默认 radix62 定长 11 位
```

两种形态与业务标识**完全对称**（同一号源、同样走 `IdConverter`）；共享生成器的转换器配置项是
`cosid.segment.share.converter.*` / `cosid.snowflake.share.converter.*`（如加日期前缀、改长度）。
技术 ID 常要写进日志、HTTP 头、MQ key，**定长无符号**的字符串比十进制更好对齐与排序——
所以这条不是 `Long.toString(getShareId())` 的语法糖（两者文本不同）。

| 维度 | `getId("ORDER")` | `getShareId()` |
| --- | --- | --- |
| 是否要声明 | 要（`cosid.segment.provider.ORDER` / `cosid.snowflake.provider.ORDER`） | **不用**（CosId 的 `share.enabled` 默认 `true`） |
| 号源 | 该业务专属（各自 `offset` / 位分配） | 全应用共用一个 |
| 形态可控 | ✅ 位数、起点、转换器都可控 | ❌ 由模式与 CosId 默认值决定 |
| 适用 | 业务编号（订单号 / 会员号 / 流水号） | 技术 ID（traceId / 消息 ID / 幂等键） |

> ⚠️ **不要用它生成业务编号**：它的形态随模式变化（号段模式是短号、雪花模式约 19 位），
> 换模式就破坏位数契约；而且代码里看不出这个号属于哪个业务。
> 也**不要为省一个标识而借用别人的业务标识**（例如拿 `ORDER` 发 traceId）——那会污染真实业务的号段。
>
> 为什么它能当“全局号源”：CosId 官方要求同一应用最多一个共享生成器，这与本框架的「两种模式互斥」正好一致。
> 被显式关闭（`share.enabled: false`）时它会 fail-fast 并提示开关名，不会静默返回哨兵值。

## 迁移注意事项（号段模式从 Leaf 切过来）

1. **offset 必须 > 原 `T_LEAF_ALLOC.max_id`**：否则新号与历史号重叠，这是唯一会导致数据事故的点。
2. **切换窗口内 Leaf 不能还在发放**：老实例用 Leaf、新实例用 CosId 会各自发号 → 重号。**必须停机切换，不可灰度并存**。
3. **一旦生成过新号就不能回退**到 Leaf（号段会重叠）。
4. 旧表 `T_LEAF_ALLOC` 在切换完成后可保留一段时间用于对账，再清理。
5. 雪花模式**不适用**上述 `offset` 衔接：它的 ID 与历史号没有可比性，若已有历史业务编号，只能继续用号段模式。

## 模块结构

```
fireworks-id-spring-boot-starter/
├── pom.xml                                          # cosid-spring-boot-starter + cosid-jdbc
├── script/db/cosid.sql                              # 号段模式建表脚本（雪花模式不需要；不进 jar）
└── src/main/
    ├── java/com/yzm/fireworks/id/
    │   ├── IdUtil.java                              # 唯一对外 API（fail-fast）
    │   ├── IdAutoConfiguration.java                 # 把 IdGeneratorProvider 绑定给静态 API（唯一自动配置类）
    │   └── IdModeConflictEnvironmentPostProcessor.java  # 模式互斥校验
    └── resources/META-INF/
        ├── spring.factories                         # 注册上面那个 EnvironmentPostProcessor
        └── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports   # 注册 IdAutoConfiguration
```

本模块对启动期的参与只有两处、都很小：

- **`IdAutoConfiguration`**：把 CosId 的 `IdGeneratorProvider` 绑定给静态 API `IdUtil`（静态类拿不到依赖注入），
  绑定点就是 `IdUtil` Bean 的**构造器**——绑的是引用，所以不要求 CosId 的生成器注册器先执行完。
  CosId 自身的配置仍全部由 CosId 的自动配置负责，本类不参与。
- **`IdModeConflictEnvironmentPostProcessor`**：模式互斥校验，只读不写。

顺带：**本模块不依赖 `fireworks-common`**——静态 API 需要的 provider 由自己的自动配置绑定，不用 `SpringContextHolder`。

建表脚本放在**模块根目录**的 `script/db/` 下（不进 jar）——与主流开源项目的惯例一致（如 Seata 的 `script/**/db/`），
部署时从源码仓库取用；既不把 DDL 混进代码资源目录，也不占用 classpath 命名空间。

## 依赖说明

- `me.ahoo.cosid:cosid-spring-boot-starter`（版本由 `cosid-bom` 管理）
- `me.ahoo.cosid:cosid-jdbc`——JDBC **号段**分发器。在 CosId starter 中是 `optional`，**必须显式引入**
- 雪花模式的机器号分发：`stateful_set` 无需额外依赖（实现在 CosId 核心包内）；
  `redis` 的适配包 `cosid-spring-redis` **已随本 starter 引入**（只需应用侧有 `StringRedisTemplate`）
- 不再引入 Leaf（连带 mysql 驱动、mybatis、mybatis-spring、guava/jackson 硬编码版本、perf4j 一起消失）
- **不依赖 `fireworks-common`**：静态 API 所需的 `IdGeneratorProvider` 由本模块的 `IdAutoConfiguration` 自行绑定

## 为什么号段模式用 JDBC 分发器而不是 Redis

Redis 分发器把号段分配状态放在 Redis：**Redis 丢数据（主从切换、未持久化）会导致号段重发 → 重号**。
业务编号一旦重号不可挽回，因此选择 PostgreSQL 持久化分发（代价是每次号段耗尽时一次数据库往返，量级可忽略）。
（注意：这是**号段**分发器的选择；**雪花模式的机器号**分发用 Redis 是安全的，因为机器号只需唯一、不需要持久递增状态。）

## 能力边界

- 不提供「连续编号」保证（两种模式都不保证，号段模式会跳号、雪花模式按位分配）。
- **`getShareId()` 的形态不稳定**：它随模式与 CosId 默认值变化，只承诺“唯一”，因此只适合技术 ID（见上）。
- 不提供机器号的自动运维与前置校验：雪花模式下机器号由所选分发器负责（推荐 `redis` / `stateful_set`），配置有误时由 CosId 自行报错。
- 不在框架内实现 `jdbc` 机器号分发器的 PG 方言覆盖——CosId 上游没有覆盖入口，需要时应自行实现 `MachineIdDistributor` Bean。
- **不定义任何自有配置项**：配置一律使用 CosId 原生属性，避免"一半框架、一半 CosId"的双重心智模型。
