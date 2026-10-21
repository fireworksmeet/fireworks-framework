# fireworks-message-spring-boot-starter

消息渠道适配层：把一条已经决定要发的消息交给某个渠道（短信 / 邮件 / 实时推送），并把渠道差异关在模块内部。

它**不是**通知平台：不做渠道选择、用户偏好、重试调度与发送记录。

## 能力

- 渠道：阿里云短信、腾讯云短信、SMTP 邮件、Nchan 实时推送
- 统一门面：`MessageSender#send(MessageEnvelope)`
- 结果两态：`RetryableFailure`（环境恢复后可再试）/ `PermanentFailure`（重试无意义）
- 限流：Redis GCRA，按渠道配置后生效
- 事件钩子：`MessageListener`，用于业务落库、审计与指标

明确不做：异步线程池、去重、幂等、发送记录落库、框架内重试。需要「不丢」时由业务使用 `fireworks-outbox-spring-boot-starter`。

## 快速开始

```xml
<dependency>
    <groupId>com.yzm.fireworks</groupId>
    <artifactId>fireworks-message-spring-boot-starter</artifactId>
</dependency>
```

```yaml
fireworks:
  message:
    enabled: true
    channels:
      sms:
        enabled: true
        provider: ALIYUN          # ALIYUN | TENCENT
        sign-name: 签名
        template-id: SMS_123456
        aliyun:
          access-key-id: ...
          access-key-secret: ...
        rate-limit:
          limit: 100
          window: 60s
      email:
        enabled: true
        from-name: 通知中心
        rate-limit:
          limit: 50
          window: 60s
      realtime:
        enabled: true
        nchan:
          url: http://nchan:80        # 仅用于拼接消息里的相对 path
          channel-id-delimiter: ","   # 必须与 Nginx 的 nchan_channel_id_split_delimiter 一致
```

发送短信：

```java
MessageEnvelope envelope = MessageEnvelope.builder()
        .messageId(UUID.randomUUID().toString())
        .channel(ChannelType.SMS)
        .recipient(Recipient.builder().phoneNumbers(List.of("13800000000")).build())
        .template("SMS_123456")
        .params(Map.of("code", "123456"))
        .build();

ChannelResult result = messageSender.send(envelope);
```

推送实时消息：

```java
MessageEnvelope envelope = MessageEnvelope.builder()
        .messageId(UUID.randomUUID().toString())
        .channel(ChannelType.REALTIME)
        // 频道 ID 的含义由业务定义：这里是"每个用户一个频道"
        .recipient(Recipient.builder().channelIds(List.of("u1", "u2")).build())
        .title("订单已发货")
        .content("您的订单已发出")
        .realtimeData(new OrderShippedEvent("SF123", "2026-10-20"))  // 任意对象，直接放入，无需转 Map
        .realtimePath("/pub/web/user")  // 最终地址：http://nchan:80/pub/web/user/u1,u2
        .build();
```

渠道专用入参直接放在 `MessageEnvelope` 上，用渠道前缀命名，只有对应渠道读取，其余渠道忽略：`realtimePath`（实时通道，必填）、`realtimeData`（实时业务载荷）、`emailHtml`（邮件是否 HTML，默认 false）、`rateLimitScope`（限流维度后缀，默认省略）。`metadata` 是纯业务扩展 Map，框架不读取。

## 结果语义

| 结果 | 含义 | 业务动作 |
| --- | --- | --- |
| `Success` | 渠道已接受（实时通道为「已投递到网关」） | 无需处理 |
| `RetryableFailure` | 环境恢复后同一请求可能成功 | 可重试；与 outbox 集成时抛普通异常 |
| `PermanentFailure` | 参数、配置或账号问题 | 不要重试；与 outbox 集成时抛 `OutboxNonRetryableException` |

框架不做重试，`RetryableFailure` 只是给业务或 outbox 的信号。

`messageId` 由业务提供且必填：框架不生成、不改写，缺失时抛 `IllegalArgumentException`。它是客户端去重与链路追踪的唯一依据，必须在业务事务内确定。

## 与 outbox 集成

```java
@Component
public class SmsOutboxHandler implements OutboxHandler {

    private final MessageSender messageSender;

    @Override
    public String eventType() {
        return "SMS_SEND";
    }

    @Override
    public void handle(OutboxEvent event) throws Exception {
        ChannelResult result = messageSender.send(deserialize(event));
        if (result instanceof ChannelResult.RetryableFailure) {
            throw new IllegalStateException(result.messageId());
        }
        if (result instanceof ChannelResult.PermanentFailure) {
            throw new OutboxNonRetryableException(result.messageId());
        }
    }
}
```

`messageId` 必须在写 outbox 时确定，重放时复用记录中的值，客户端据此去重。框架不生成 ID。

## 渠道契约

- **短信**：必须使用厂商模板（`template` 传模板 ID），参数只来自 `params`；手机号支持 E.164 或国内 11 位，框架不改写；腾讯云、阿里云均原生接受这两种格式，仅当实现所调 API 强制 E.164 时才由其自行补全国家码（国际号码须自带国家码）；签名与模板 ID 可走配置默认值
- **邮件**：主题（`title`）必填；`template.code` 为 Thymeleaf 模板路径（不带 `.html`），模板文件由业务自行提供（放在自己的 `templates/` 下），框架不内置模板；无模板时直接发送 `content`，不做变量替换；正文是否按 HTML 发送由 `emailHtml` 决定，默认 false
- **实时**：Nchan 只负责 HTTP publish，框架不建模平台 / 用户 / 群组。`realtimePath` 必填（绝对 URL 直接使用，相对路径拼在 `nchan.url` 之后），`recipient.channelIds` 必填（含义由业务定义），频道串按 `max-channel-path-bytes` 与 `max-channels-per-publish` 自动分批；HTTP 超时复用 `spring.http.client.*`、连接池复用 `fireworks.rest-client.*`，本模块不单独设置

## 配置项

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `fireworks.message.enabled` | `false` | 模块开关 |
| `fireworks.message.channels.sms.provider` | 无 | `ALIYUN` 或 `TENCENT`，两个实现时必填 |
| `fireworks.message.channels.*.rate-limit` | 无 | 不配置即不限流 |
| `fireworks.message.channels.realtime.nchan.channel-id-delimiter` | `,` | 频道 ID 分隔符，须与 Nginx `nchan_channel_id_split_delimiter` 一致；单个可打印 ASCII 字符 |
| `fireworks.message.channels.realtime.nchan.max-channel-path-bytes` | `4096` | 单次 publish 频道串上限 |
| `fireworks.message.channels.realtime.nchan.max-channels-per-publish` | `200` | 单次 publish 频道数上限 |

限流只有 Redis 实现；Redis 不可用时**阻断发送**并返回 `RetryableFailure`，防止失去保护后超发。
