package com.yzm.fireworks.msg.core.sender;

import com.yzm.fireworks.msg.core.message.BaseMessage;
import com.yzm.fireworks.msg.core.message.MessageResult;
import com.yzm.fireworks.msg.core.ratelimit.RateLimiter;
import com.yzm.fireworks.msg.properties.MessageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 带限流能力的抽象消息发送基类
 *
 * @param <T> 消息类型
 * @author JYuan
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractRateLimitSender<T extends BaseMessage> implements MessageSender<T> {

    protected final MessageProperties properties;
    protected final RateLimiter rateLimiter;

    @Override
    public MessageResult send(T message) {
        MessageProperties.RateLimitConfig config = getRateLimitConfig();
        List<String> acquiredKeys = new ArrayList<>();
        boolean limitExceeded = false;

        try {
            // 1. 限流检查
            if (config != null && config.isEnabled()) {
                String[] recipients = getRecipients(message);
                if (!ObjectUtils.isEmpty(recipients)) {
                    for (String recipient : recipients) {
                        String key = config.getKeyPrefix() + recipient;
                        if (rateLimiter.allowRequest(key, config.getLimit(), config.getWindowSeconds())) {
                            acquiredKeys.add(key);
                        } else {
                            log.warn("[{}] Rate limit exceeded for recipient: {}", getChannelName(), recipient);
                            limitExceeded = true;
                            break;
                        }
                    }
                }
            }

            if (limitExceeded) {
                rollback(acquiredKeys);
                return MessageResult.failed(message.getMessageId(), getChannelName() + " rate limit exceeded");
            }

            // 2. 执行实际发送
            MessageResult result = doSend(message);

            // 3. 结果检查：如果业务层面返回失败，也回滚令牌
            if (result == null || !result.isSuccess()) {
                rollback(acquiredKeys);
            }

            return result;
        } catch (Exception e) {
            log.error("[{}] Failed to send message: {}", getChannelName(), message.getMessageId(), e);
            // 4. 异常回滚
            rollback(acquiredKeys);
            return MessageResult.failed(message.getMessageId(), e.getMessage());
        }
    }

    /**
     * 实际的消息发送逻辑，由子类实现
     */
    protected abstract MessageResult doSend(T message) throws Exception;

    /**
     * 获取发送对象（手机号或邮箱地址）
     */
    protected abstract String[] getRecipients(T message);

    /**
     * 获取当前渠道的限流配置
     */
    protected abstract MessageProperties.RateLimitConfig getRateLimitConfig();

    /**
     * 获取渠道名称（日志用）
     */
    protected abstract String getChannelName();

    /**
     * 回滚令牌（删除限流记录）
     */
    private void rollback(List<String> keys) {
        if (ObjectUtils.isEmpty(keys)) {
            return;
        }
        for (String key : keys) {
            try {
                rateLimiter.removeRequest(key);
            } catch (Exception e) {
                log.error("Failed to rollback rate limit key: {}", key, e);
            }
        }
    }
}
