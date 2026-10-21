package com.yzm.fireworks.message.api;

import com.yzm.fireworks.message.domain.ChannelMessage;
import com.yzm.fireworks.message.domain.ChannelResult;
import com.yzm.fireworks.message.domain.MessageEnvelope;

/**
 * 发送门面。
 * <p>
 * 同步、无状态：校验、限流、路由到 {@link MessageProvider}，并归一化结果。
 * 不提供异步、不重试、不落库。
 *
 * @author JYuan
 */
public interface MessageSender {

    /**
     * 发送一条消息（业务入口）。
     * <p>
     * 与 SPI 的 {@link MessageProvider#send(ChannelMessage)} )} 同名但层次不同：本方法负责校验、限流、
     * 路由与结果归一化；SPI 只执行一次外部调用。
     *
     * @param envelope 统一消息模型
     * @return 发送结果，不会为 {@code null}
     */
    ChannelResult send(MessageEnvelope envelope);
}
