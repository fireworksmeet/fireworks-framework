package com.yzm.fireworks.msg.service;

import com.yzm.fireworks.id.IdType;
import com.yzm.fireworks.id.IdUtil;
import com.yzm.fireworks.msg.MessageAutoConfiguration;
import com.yzm.fireworks.msg.core.message.BaseMessage;
import com.yzm.fireworks.msg.core.message.MessageResult;
import com.yzm.fireworks.msg.core.message.WebSocketMessage;
import com.yzm.fireworks.msg.enums.MessageStatus;
import com.yzm.fireworks.msg.enums.MessageType;
import com.yzm.fireworks.msg.exception.MessagePushException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 统一消息推送服务
 *
 * <p>提供同步和异步两种发送方式：
 * <ul>
 *   <li>{@link #sendSync(BaseMessage)} — 同步阻塞，直接返回结果，适合需要立即知道发送状态的场景</li>
 *   <li>{@link #sendAsync(BaseMessage)} — 异步非阻塞，返回 {@link CompletableFuture}，
 *       适合对延迟敏感、不需要等待发送结果的场景</li>
 * </ul>
 *
 * <p>异步实现说明：
 * <ul>
 *   <li>线程池由 {@code fireworks.message.push.async} 配置项控制，
 *       通过 {@link MessageAutoConfiguration} 注册为 {@code msgAsyncExecutor} Bean</li>
 *   <li>当 {@code async.enabled=false} 时，{@link #sendAsync} 降级为同步执行，
 *       方便测试环境复现问题</li>
 *   <li>异步任务内部的异常不会向上抛出，而是通过 {@link CompletableFuture#exceptionally} 处理，
 *       调用方可链式处理：
 *       <pre>{@code
 *       messagePushService.sendAsync(message)
 *           .thenAccept(result -> log.info("sent: {}", result.isSuccess()))
 *           .exceptionally(ex -> { log.error("failed", ex); return null; });
 *       }</pre>
 *   </li>
 * </ul>
 *
 * @author JYuan
 */
@Slf4j
public class MessagePushService {

    private final MessageRouterService routerService;
    private final MessageDeduplicationService deduplicationService;
    private final MessageRecordService recordService;
    /** 为 null 时 sendAsync 降级为同步（async.enabled=false 场景） */
    private final Executor asyncExecutor;

    public MessagePushService(MessageRouterService routerService,
                              MessageDeduplicationService deduplicationService,
                              MessageRecordService recordService,
                              Executor asyncExecutor) {
        this.routerService = routerService;
        this.deduplicationService = deduplicationService;
        this.recordService = recordService;
        this.asyncExecutor = asyncExecutor;
    }

    // ==================== 同步发送 ====================

    /**
     * 同步发送消息
     *
     * <p><b>方案设计：兼顾性能与一致性</b>
     * <ol>
     *   <li><b>第一层（Redis 拦截）</b>：前置高性能检查，拦截绝大部分重复流量，减轻 DB 压力。</li>
     *   <li><b>第二层（DB 唯一索引）</b>：利用数据库 {@code uk_message_id} 索引进行终极原子性去重。</li>
     *   <li><b>异常补偿</b>：若因非重复原因（如数据库波、网络故障）导致记录失败，立即清除 Redis 状态，允许重试。</li>
     * </ol>
     *
     * @param message 消息体，{@code messageId} 必填
     * @return 发送结果
     */
    public MessageResult sendSync(BaseMessage message) {
        validateMessageId(message);

        // 1. Redis 前置拦截
        boolean isDuplicateAtRedis = deduplicationService.isDuplicate(message);
        if (isDuplicateAtRedis) {
            log.warn("Duplicate message intercepted by Redis: messageId={}", message.getMessageId());
            return MessageResult.failed(message.getMessageId(), "Duplicate message (Redis)");
        }

        // 2. 尝试创建数据库记录
        try {
            recordService.createRecord(message, MessageStatus.SENDING);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 兜底：数据库主键冲突（即便 Redis 漏掉，DB 也能挡住）
            log.warn("Duplicate message detected by DB constraint: messageId={}", message.getMessageId());
            // 如果是重复，不需要清理 Redis（因为确实是重复）
            return MessageResult.failed(message.getMessageId(), "Duplicate message (DB)");
        } catch (Exception e) {
            // 重要：非重复原因导致的失败（如 DB 宕机、连接超时）
            log.error("Failed to create message record due to unexpected error: messageId={}",
                    message.getMessageId(), e);
            // 执行补偿：清除 Redis 占用，否则在去重窗口内该消息无法再次被发送
            deduplicationService.clearDeduplication(message);
            return MessageResult.failed(message.getMessageId(), "System error: " + e.getMessage());
        }

        // 3. 执行真实发送
        try {
            MessageResult result = routerService.route(message);
            // 4. 发送成功，将短时锁转为长效去重锁
            if (result.isSuccess()) {
                deduplicationService.confirmDeduplication(message);
            }
            safeUpdateRecord(result);
            return result;
        } catch (Exception e) {
            log.error("Exception during message routing: messageId={}", message.getMessageId(), e);
            MessageResult result = MessageResult.failed(message.getMessageId(), e.getMessage());
            safeUpdateRecord(result);
            return result;
        }
    }

    /**
     * 安全更新记录（捕获异常，避免影响主流程）
     */
    private void safeUpdateRecord(MessageResult result) {
        try {
            recordService.updateRecord(result);
        } catch (Exception e) {
            log.error("Failed to update message record: messageId={}, status={}",
                    result.getMessageId(), result.getStatus(), e);
        }
    }

    // ==================== 异步发送 ====================

    /**
     * 异步发送消息
     *
     * <p>内部逻辑与 {@link #sendSync} 完全一致，区别仅在于在独立线程池中执行。
     * 当 {@code async.enabled=false}（即 {@code asyncExecutor} 为 null）时，
     * 降级为同步执行并返回已完成的 {@link CompletableFuture}，行为完全等价于 {@link #sendSync}。
     *
     * <p>异常处理：内部已添加默认异常日志记录，调用方仍可通过 {@code .exceptionally()} 添加自定义处理。
     *
     * @param message 消息体，{@code messageId} 必填
     * @return 发送结果的 {@link CompletableFuture}
     */
    public CompletableFuture<MessageResult> sendAsync(BaseMessage message) {
        if (asyncExecutor == null) {
            // async.enabled=false，降级为同步，包装成已完成的 Future 返回
            log.debug("Async executor is disabled, falling back to sync send: messageId={}", message.getMessageId());
            return CompletableFuture.completedFuture(sendSync(message));
        }

        // 提前获取 messageId，避免在异常处理中访问可能为 null 的 message
        String messageId = message != null ? message.getMessageId() : "unknown";

        return CompletableFuture.supplyAsync(() -> sendSync(message), asyncExecutor)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Async message send failed: messageId={}", messageId, ex);
                    }
                });
    }

    /**
     * 异步发送到用户私有频道
     */
    public CompletableFuture<MessageResult> sendToUserAsync(String messageId, String platform,
                                                            String userId, String title, String content) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(messageId).platform(platform)
                .userIds(new String[]{userId}).title(title).content(content)
                .build();
        return sendAsync(message);
    }

    /**
     * 异步发送到群组频道
     */
    public CompletableFuture<MessageResult> sendToGroupAsync(String messageId, String platform,
                                                             String groupId, String title, String content) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(messageId).platform(platform)
                .groupId(groupId).title(title).content(content)
                .build();
        return sendAsync(message);
    }

    /**
     * 异步广播消息
     */
    public CompletableFuture<MessageResult> broadcastAsync(String messageId, String platform,
                                                           String title, String content) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(messageId).platform(platform)
                .title(title).content(content)
                .build();
        return sendAsync(message);
    }

    // ==================== 同步快捷方法（保持原有 API 不变） ====================

    public MessageResult sendToUser(String messageId, String platform,
                                    String userId, String title, String content) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(messageId).platform(platform)
                .userIds(new String[]{userId}).title(title).content(content)
                .build();
        return sendSync(message);
    }

    public MessageResult sendToGroup(String messageId, String platform,
                                     String groupId, String title, String content) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(messageId).platform(platform)
                .groupId(groupId).title(title).content(content)
                .build();
        return sendSync(message);
    }

    public MessageResult broadcast(String messageId, String platform, String title, String content) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(messageId).platform(platform)
                .title(title).content(content)
                .build();
        return sendSync(message);
    }

    // ==================== 工具方法 ====================

    public String generateMessageId(MessageType messageType) {
        if (ObjectUtils.isEmpty(messageType)) {
            throw new MessagePushException("Message type is required");
        }
        long id = IdUtil.getId(IdType.SERIAL_NUMBER);
        return switch (messageType) {
            case WEBSOCKET -> "ws_" + id;
            case SMS -> "sms_" + id;
            case EMAIL -> "email_" + id;
        };
    }

    private void validateMessageId(BaseMessage message) {
        if (!StringUtils.hasText(message.getMessageId())) {
            throw new MessagePushException("MessageId is required");
        }
    }
}