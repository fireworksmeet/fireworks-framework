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

执行 `resources/db.sql` 创建 `message_record` 表（含 `uk_message_id` 唯一索引，是 DB 层去重的关键）。

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
- `messageId` 是去重与记录的主键，**广播消息必须提供**。

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
