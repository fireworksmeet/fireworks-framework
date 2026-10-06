# fireworks-msg-spring-boot-starter

统一消息推送 Starter，支持 **WebSocket**、**短信**（阿里云 / 腾讯云）、**邮件** 三类渠道，并提供**消息去重**、**限流**、**异步发送**、**发送记录持久化**等能力。

## 功能特性

- **多渠道推送**：WebSocket（Nchan）、短信（阿里云 / 腾讯云）、邮件（Thymeleaf 模板）。
- **统一 API**：`MessagePushService` 提供同步（`sendSync`）与异步（`sendAsync`）两种发送入口，自动按消息类型路由。
- **消息去重**：Redis + DB 唯一索引双层去重，保证同一消息不重复发送。
- **限流**：基于 Redisson 令牌桶（`RedissonRateLimiter`），按渠道独立配置。
- **异步线程池**：默认异步发送，`CallerRunsPolicy` 拒绝策略（不丢消息、自然背压），MDC 上下文自动传递。
- **发送记录**：消息入库，`message_record` 表持久化发送状态。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-msg-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```

### 2. 初始化数据库

执行 `script/db/message_record.sql` 创建 `message_record` 表（含 `uk_message_id` 唯一索引，是 DB 层去重的关键）。
脚本在**模块根目录**的 `script/db/` 下（随源码交付、不进 jar）。
`send_time` / `next_retry_time` / `created_at` / `updated_at` 均使用 **`timestamptz`（带时区）**，与实体 `Instant` 字段（绝对时间点）语义一致，跨时区部署时保持时间准确。

### 3. 配置

```yaml
fireworks:
  message:
    push:
      enabled: true            # 总开关
      async:
        enabled: true          # 异步发送，默认 true
        core-pool-size: 4      # 0 表示自动：CPU+1
        max-pool-size: 8       # 0 表示自动：CPU×2
        queue-capacity: 200
      deduplication:
        enabled: true          # 去重开关
        window-seconds: 300    # 去重时间窗口
      sms:
        enabled: true
        provider: ALIYUN        # ALIYUN / TENCENT
        default-sign: 签名
        default-template-id: 模板ID
        aliyun:
          access-key-id: xxx
          access-key-secret: xxx
        rate-limit:
          enabled: true
          limit: 1              # 窗口内最大条数
          window-seconds: 60
      email:
        enabled: true
        default-from-name: 发件人
        rate-limit:
          enabled: true
          limit: 10
          window-seconds: 60
      websocket:
        enabled: true
        nchan-url: http://localhost:80
```

> 邮件需额外配置 `spring.mail.*`（`spring.mail.host/username/password` 等）。

### 4. 发送消息

注入 `MessagePushService`，构建对应类型的消息：

**同步发送 WebSocket（用户频道）：**

```java
MessageResult result = messagePushService.sendToUser(
        messagePushService.generateMessageId(MessageType.WEBSOCKET), // messageId 必填
        "ios", "userId_001", "标题", "内容");
```

**异步发送到群组：**

```java
CompletableFuture<MessageResult> future = messagePushService.sendToGroupAsync(
        messagePushService.generateMessageId(MessageType.WEBSOCKET),
        "android", "groupId_01", "标题", "内容");

future.thenAccept(r -> log.info("sent: {}", r.isSuccess()));
```

**广播：**

```java
messagePushService.broadcast(
        messagePushService.generateMessageId(MessageType.WEBSOCKET),
        "ios", "系统公告", "内容");
```

**发送短信：**

```java
SmsMessage sms = SmsMessage.builder()
        .messageId(messagePushService.generateMessageId(MessageType.SMS))
        .phoneNumbers(new String[]{"13812345678"})
        .content("您的验证码是 123456")
        .build();
MessageResult result = messagePushService.sendSync(sms);
```

**发送邮件：**

```java
EmailMessage email = EmailMessage.builder()
        .messageId(messagePushService.generateMessageId(MessageType.EMAIL))
        .emailTo(new String[]{"a@example.com"})
        .subject("通知")            // 存于 BaseMessage.content
        .content("<h1>Hello</h1>")  // 邮件正文
        .htmlEmail(true)
        .build();
MessageResult result = messagePushService.sendSync(email);
```

### 5. 消息类型与 ID

- 消息 ID 通过 `messagePushService.generateMessageId(MessageType)` 生成（依赖 `fireworks-id` 的 `IdUtil`），前缀如 `ws_`、`sms_`、`email_`。
- `messageId` 是去重与记录的主键，**广播消息必须提供**。调用方自己提供 `messageId` 时，**不需要任何 ID 配置**。

**取号来源由 `fireworks.message.push.id.domain` 决定**：

| 配置 | 取号来源 | 说明 |
| --- | --- | --- |
| **不配置（默认）** | `IdUtil.getShareIdAsString()`（CosId 共享生成器） | **开箱即用**：无需在 CosId 里声明任何 provider，也不占用业务号段 |
| 配成业务标识 | `IdUtil.getIdAsString(该值)` | 需要控制起点 / 位数时才用，且必须同步声明同名 provider |

**ID 形态**：数字部分是 CosId 转换器输出的 **radix62（0-9A-Za-z）定长 11 位**，例如 `email_000000001Ii`
（62<sup>11</sup> &gt; `Long.MAX_VALUE`，所以任何 long 都装得下）。三个由此而来的性质：

- **定长 11 位**：号段模式（短号）与雪花模式（19 位十进制）渲染成**同一宽度**，形态不随模式变化；
- **字典序 = 数值序**：定长补零才成立，因此 `ORDER BY message_id` 等价于按取号先后排序（十进制文本没有补零，字典序会错：`"10" < "9"`）；
- 比十进制（最多 19 位）和 UUID（36 位）都短。

想改形态（如加日期前缀 `20261006_…`）用 `cosid.segment.share.converter.*` / `cosid.snowflake.share.converter.*`；
配了 `id.domain` 时用该 provider 自己的 `converter.*`。若不想依赖任何 ID 基础设施，也可把
`MessagePushService#nextMessageId()` 换成 `IdUtil.getUUID()`（32 位紧凑格式、**无序**，唯一索引会随机插入）。

```yaml
fireworks:
  message:
    push:
      id:
        domain: MESSAGE_ID          # 可选；不配则用共享生成器
```

要指定专属标识时，还必须在 CosId 里声明**同名** provider：

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true                   # 与 cosid.snowflake.enabled 互斥
    provider:
      MESSAGE_ID:                   # ← 与 id.domain 完全一致（区分大小写）
        offset: 1                   # 新序列的起点；只有「沿用旧域」时才必须 > 该域的历史 max_id
        step: 10000
```

> ⚠️ 上面只是 **provider 部分**；完整的号段配置还包括**建表脚本**与 PostgreSQL 必须覆盖的两条 SQL
> （以及 `jdbc` 分发器设置），见 `fireworks-id-spring-boot-starter/README.md` 的「模板 1」。
> **只配这一段会因缺表 / SQL 方言而失败。**
> 若用雪花模式，同样声明 `cosid.snowflake.provider.MESSAGE_ID` 即可（`IdUtil` 对两种模式行为一致）。

> ⚠️ **不要用业务序列给消息发号**：本模块此前的固定契约是 `SERIAL_NUMBER`——那是「用于流水号」的
> **业务序列**，用它发消息会消耗业务流水号的号段，并在流水号里留下来源不明的跳号。
> 消息 ID 是技术标识，默认的共享生成器正为此设计。存量部署若要保留旧行为，可显式配
> `id.domain: SERIAL_NUMBER`（不推荐，仍会污染流水号）；直接切默认则消息 ID 换号源，不影响唯一性与 `uk_message_id` 去重。

> ⚠️ 用默认（不配 `id.domain`）时依赖 CosId 的共享生成器（`cosid.segment.share.enabled` /
> `cosid.snowflake.share.enabled`，**默认即 true**）。若按 `fireworks-id` 的 README 把 share 关掉以减少号段占用，
> 首次发消息会 fail-fast，报错信息会提示该开关。

## 去重机制（双层）

1. **Redis 前置拦截**：`MessageDeduplicationService.isDuplicate` 用 `SETNX` 短锁拦截，发送成功后再 `confirmDeduplication` 转为长效锁。
2. **DB 唯一索引兜底**：`uk_message_id` 索引保证终极原子性去重。
3. **异常补偿**：非重复原因导致记录失败时清除 Redis 锁，允许重试。

## 限流

各渠道可独立配置 `rate-limit`，基于 Redisson 令牌桶实现，防止短时间内过度推送触发渠道风控。

## 核心类速览

| 类 | 说明 |
| --- | --- |
| `MessagePushService` | 统一发送入口（同步 / 异步 / 快捷方法） |
| `MessageRouterService` | 按消息类型路由到对应 Sender |
| `MessageDeduplicationService` | 消息去重（Redis + DB） |
| `MessageRecordService` | 发送记录持久化 |
| `SmsSender` / `EmailSender` / `WebSocketSender` | 各渠道发送器 |
| `RateLimiter` / `RedissonRateLimiter` | 限流 |
| `BaseMessage` / `SmsMessage` / `EmailMessage` / `WebSocketMessage` | 消息体 |
| `MessageResult` | 发送结果 |

## 注意事项

- 依赖 `fireworks-redis-spring-boot-starter`（去重 / 限流）与 `fireworks-id-spring-boot-starter`（消息 ID）。
- 异步线程池满时使用 `CallerRunsPolicy` 在调用线程同步执行，不会丢消息。
