package com.yzm.fireworks.message.domain;

/**
 * 发送结果。
 * <p>
 * 两个失败分支互斥：{@link RetryableFailure} 表示环境恢复后同一请求可能成功，
 * {@link PermanentFailure} 表示重试没有意义。框架不重试，分类供业务或 outbox 消费。
 *
 * @author JYuan
 */
public sealed interface ChannelResult {

    /**
     * 消息标识。
     *
     * @return messageId
     */
    String messageId();

    /**
     * 是否成功。
     *
     * @return true 表示 {@link Success}
     */
    default boolean success() {
        return this instanceof Success;
    }

    /**
     * 成功。
     *
     * @param messageId  消息标识
     * @param externalId 厂商侧回执 ID，可为空
     */
    record Success(String messageId, String externalId) implements ChannelResult {
    }

    /**
     * 暂时失败，可重试。
     *
     * @param messageId 消息标识
     * @param code      稳定的错误码
     * @param message   描述
     * @param cause     原始异常，可为空
     */
    record RetryableFailure(String messageId, String code, String message, Throwable cause)
            implements ChannelResult {
    }

    /**
     * 永久失败，重试无意义。
     *
     * @param messageId 消息标识
     * @param code      稳定的错误码
     * @param message   描述
     */
    record PermanentFailure(String messageId, String code, String message) implements ChannelResult {
    }
}
