package com.yzm.fireworks.message.domain;

/**
 * 各渠道强类型入参的公共契约。
 * <p>
 * 只要求携带 {@code messageId}：渠道实现需要它构造 {@link ChannelResult}。
 *
 * @author JYuan
 */
public interface ChannelMessage {

    /**
     * 消息标识，由门面确定并在整个发送链路中保持不变。
     *
     * @return messageId
     */
    String getMessageId();
}
