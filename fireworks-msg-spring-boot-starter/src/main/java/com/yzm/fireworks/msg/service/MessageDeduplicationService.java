package com.yzm.fireworks.msg.service;

import com.yzm.fireworks.msg.core.message.BaseMessage;
import com.yzm.fireworks.msg.core.message.EmailMessage;
import com.yzm.fireworks.msg.core.message.SmsMessage;
import com.yzm.fireworks.msg.core.message.WebSocketMessage;
import com.yzm.fireworks.msg.exception.MessagePushException;
import com.yzm.fireworks.msg.properties.MessageProperties;
import com.yzm.fireworks.msg.utils.DeduplicationUtil;
import com.yzm.fireworks.redis.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 消息去重服务
 *
 * @author JYuan
 */
@Slf4j
public class MessageDeduplicationService {

    private final MessageProperties properties;

    public MessageDeduplicationService(MessageProperties properties) {
        this.properties = properties;
    }

    private String buildRedisKey(String deduplicationKey) {
        return properties.getDeduplication().getRedisKeyPrefix() + deduplicationKey;
    }

    /**
     * 检查消息是否重复（两阶段锁定：第一阶段 - 处理中锁定）
     *
     * <p>初始设置一个极短的过期时间（processingWindowSeconds），
     * 即使后续步骤崩溃且清理失败，锁也会在短时间内自动释放。
     */
    public boolean isDuplicate(BaseMessage message) {
        if (!properties.getDeduplication().isEnabled() || !message.isNeedDeduplication()) {
            return false;
        }

        String deduplicationKey = generateDeduplicationKey(message);
        String redisKey = buildRedisKey(deduplicationKey);

        // 初始占用：使用极短的处理中窗口
        Boolean success = RedisUtil.setNx(redisKey,
                "1",
                properties.getDeduplication().getProcessingWindowSeconds(),
                TimeUnit.SECONDS);

        boolean isDuplicate = !Boolean.TRUE.equals(success);

        if (isDuplicate) {
            log.warn("Duplicate message detected (Processing/Completed): messageId={}, key={}",
                    message.getMessageId(), deduplicationKey);
        }

        return isDuplicate;
    }

    /**
     * 确认去重（两阶段锁定：第二阶段 - 正式去重）
     *
     * <p>在消息成功发送后调用，将短时锁转换为正式的长效去重 Key。
     */
    public void confirmDeduplication(BaseMessage message) {
        if (!properties.getDeduplication().isEnabled() || !message.isNeedDeduplication()) {
            return;
        }

        try {
            String deduplicationKey = generateDeduplicationKey(message);
            String redisKey = buildRedisKey(deduplicationKey);

            // 延长过期时间为正式窗口
            RedisUtil.expire(redisKey,
                    properties.getDeduplication().getWindowSeconds(),
                    TimeUnit.SECONDS);
            log.debug("Deduplication confirmed for messageId: {}", message.getMessageId());
        } catch (Exception e) {
            log.warn("Failed to confirm deduplication for messageId: {}", message.getMessageId(), e);
        }
    }

    /**
     * 生成去重 key
     *
     * <p><b>修复：</b>当 messageId 存在时优先以 messageId 直接生成唯一 key，
     * 不再额外拼接 content/platform（冗余且可能破坏唯一性语义）。
     * 仅当 messageId 不存在时，降级使用收件人+内容+平台的组合 hash。
     *
     * <p><b>广播消息要求：</b>广播消息（无 userId 和 groupId）必须提供 messageId，
     * 否则无法生成可靠的去重键。
     */
    private String generateDeduplicationKey(BaseMessage message) {
        // 修复：优先使用 messageId——messageId 本身已语义唯一，无需再拼 content/platform
        if (StringUtils.hasText(message.getMessageId())) {
            return DeduplicationUtil.generateByMessageId(message.getMessageId());
        }

        // messageId 不存在时，按消息类型降级
        switch (message) {
            case WebSocketMessage wsMsg -> {
                if (StringUtils.hasText(wsMsg.getGroupId())) {
                    return DeduplicationUtil.generateGroupDeduplicationKey(
                            wsMsg.getGroupId(), wsMsg.getContent(), wsMsg.getPlatform());
                } else if (!ObjectUtils.isEmpty(wsMsg.getUserIds())) {
                    return DeduplicationUtil.generateDeduplicationKey(
                            wsMsg.getUserIds()[0], wsMsg.getContent(), wsMsg.getPlatform());
                }
                // 广播消息必须提供 messageId
                throw new MessagePushException(
                        "Broadcast message must provide messageId for deduplication");
            }
            case SmsMessage smsMsg -> {
                if (smsMsg.getPhoneNumbers() != null && smsMsg.getPhoneNumbers().length > 0) {
                    return DeduplicationUtil.generateBatchDeduplicationKey(
                            smsMsg.getPhoneNumbers(), smsMsg.getContent(), smsMsg.getPlatform());
                }
            }
            case EmailMessage emailMsg -> {
                if (emailMsg.getEmailTo() != null && emailMsg.getEmailTo().length > 0) {
                    return DeduplicationUtil.generateBatchDeduplicationKey(
                            emailMsg.getEmailTo(), emailMsg.getContent(), emailMsg.getPlatform());
                }
            }
            default -> {
            }
        }

        throw new MessagePushException("Invalid message");
    }

    /**
     * 清除去重记录
     */
    public void clearDeduplication(BaseMessage message) {
        if (message == null) {
            return;
        }
        try {
            String deduplicationKey = generateDeduplicationKey(message);
            clearDeduplication(deduplicationKey);
        } catch (Exception e) {
            log.warn("Failed to clear deduplication key for messageId: {}", message.getMessageId(), e);
        }
    }

    /**
     * 清除去重记录
     */
    public void clearDeduplication(String deduplicationKey) {
        String redisKey = buildRedisKey(deduplicationKey);
        RedisUtil.delete(redisKey);
    }
}