package com.yzm.fireworks.message.support;

import com.yzm.fireworks.message.domain.ChannelMessage;
import com.yzm.fireworks.message.domain.ChannelResult;
import com.yzm.fireworks.message.domain.ChannelType;
import com.yzm.fireworks.message.domain.MessageEnvelope;
import com.yzm.fireworks.message.api.MessageListener;
import com.yzm.fireworks.message.api.MessageProvider;
import com.yzm.fireworks.message.api.MessageSender;
import com.yzm.fireworks.message.exception.MessageValidationException;
import com.yzm.fireworks.message.properties.MessageProperties;
import com.yzm.fireworks.common.ratelimit.RateLimitRequest;
import com.yzm.fireworks.common.ratelimit.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

import static com.yzm.fireworks.common.constants.StringPool.UNKNOWN;

/**
 * 发送门面默认实现。
 * <p>
 * 校验 → 限流 → 路由 → 归一化结果。无状态、不重试、不落库、不生成 messageId。
 *
 * @author JYuan
 */
@Slf4j
public class DefaultMessageSender implements MessageSender {

    private static final String CODE_INVALID_CHANNEL = "INVALID_CHANNEL";
    private static final String CODE_INVALID_ARGUMENT = "INVALID_ARGUMENT";
    private static final String CODE_CHANNEL_UNAVAILABLE = "CHANNEL_UNAVAILABLE";
    private static final String CODE_RATE_LIMITED = "RATE_LIMITED";
    private static final String CODE_RATE_LIMIT_UNAVAILABLE = "RATE_LIMIT_UNAVAILABLE";

    private final MessageProviderRegistry registry;
    private final RateLimiter rateLimiter;
    private final MessageProperties properties;
    private final List<MessageListener> listeners;

    public DefaultMessageSender(MessageProviderRegistry registry,
                                RateLimiter rateLimiter,
                                MessageProperties properties,
                                List<MessageListener> listeners) {
        this.registry = registry;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.listeners = listeners == null ? List.of() : listeners;
    }

    @Override
    public ChannelResult send(MessageEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope must not be null");
        }

        String messageId = envelope.getMessageId();
        if (!StringUtils.hasText(messageId)) {
            throw new IllegalArgumentException("messageId is required, set it before send");
        }

        ChannelResult result = doSend(envelope, messageId);
        notifyListeners(envelope, result);
        return result;
    }

    private ChannelResult doSend(MessageEnvelope envelope, String messageId) {
        ChannelType channel = envelope.getChannel();
        if (channel == null) {
            return new ChannelResult.PermanentFailure(messageId, CODE_INVALID_CHANNEL, "channel is required");
        }
        if (envelope.getRecipient() == null) {
            return new ChannelResult.PermanentFailure(messageId, CODE_INVALID_ARGUMENT, "recipient is required");
        }

        MessageProvider<?> provider;
        try {
            provider = registry.resolve(channel);
        } catch (IllegalStateException e) {
            return new ChannelResult.PermanentFailure(messageId, CODE_CHANNEL_UNAVAILABLE, e.getMessage());
        }

        ChannelResult rateLimited = rateLimit(envelope, provider, messageId);
        if (rateLimited != null) {
            return rateLimited;
        }

        try {
            return invoke(provider, envelope);
        } catch (MessageValidationException e) {
            return new ChannelResult.PermanentFailure(messageId, CODE_INVALID_ARGUMENT, e.getMessage());
        } catch (Exception e) {
            log.warn("Message send failed unexpectedly: messageId={}, channel={}", messageId, channel, e);
            return new ChannelResult.RetryableFailure(messageId, UNKNOWN, e.getMessage(), e);
        }
    }

    private <M extends ChannelMessage> ChannelResult invoke(MessageProvider<M> provider, MessageEnvelope envelope) {
        return provider.send(provider.convert(envelope));
    }

    private ChannelResult rateLimit(MessageEnvelope envelope, MessageProvider<?> provider, String messageId) {
        MessageProperties.RateLimit config = properties.rateLimitOf(envelope.getChannel());
        if (config == null) {
            return null;
        }
        if (rateLimiter == null) {
            log.warn("Rate limit is configured but no RateLimiter is available, request is allowed: messageId={}",
                    messageId);
            return null;
        }

        String key = buildRateLimitKey(envelope, provider);
        try {
            boolean allowed = rateLimiter.tryAcquire(
                    new RateLimitRequest(key, config.getLimit(), config.getWindow()));
            if (allowed) {
                return null;
            }
            return new ChannelResult.RetryableFailure(messageId, CODE_RATE_LIMITED,
                    "rate limit exceeded for " + key, null);
        } catch (Exception e) {
            log.error("Rate limiter unavailable, request is blocked: messageId={}, key={}", messageId, key, e);
            return new ChannelResult.RetryableFailure(messageId, CODE_RATE_LIMIT_UNAVAILABLE,
                    "rate limiter unavailable", e);
        }
    }

    private String buildRateLimitKey(MessageEnvelope envelope, MessageProvider<?> provider) {
        StringBuilder key = new StringBuilder()
                .append(envelope.getChannel().name().toLowerCase(Locale.ROOT))
                .append(':')
                .append(provider.providerType().name().toLowerCase(Locale.ROOT));
        String scope = envelope.getRateLimitScope();
        if (StringUtils.hasText(scope)) {
            key.append(':').append(scope);
        }
        return key.toString();
    }

    private void notifyListeners(MessageEnvelope envelope, ChannelResult result) {
        for (MessageListener listener : listeners) {
            try {
                if (result.success()) {
                    listener.onSuccess(envelope, result);
                } else {
                    listener.onFailure(envelope, result);
                }
            } catch (Exception e) {
                log.warn("MessageListener failed: messageId={}, listener={}",
                        result.messageId(), listener.getClass().getName(), e);
            }
        }
    }
}
