package com.yzm.fireworks.message.api;

import com.yzm.fireworks.message.domain.ChannelMessage;
import com.yzm.fireworks.message.domain.ChannelResult;
import com.yzm.fireworks.message.domain.ChannelType;
import com.yzm.fireworks.message.domain.MessageEnvelope;
import com.yzm.fireworks.message.domain.ProviderType;

/**
 * 渠道 SPI。
 * <p>
 * {@link #convert(MessageEnvelope)} 承担渠道特定的入参校验与转换；校验失败应抛出
 * {@link com.yzm.fireworks.message.exception.MessageValidationException}，由门面转为
 * {@link ChannelResult.PermanentFailure}。
 *
 * @param <M> 渠道强类型入参
 * @author JYuan
 */
public interface MessageProvider<M extends ChannelMessage> {

    /**
     * 投递渠道类型。
     *
     * @return 渠道类型
     */
    ChannelType channelType();

    /**
     * 渠道实现类型，同一渠道内唯一。
     *
     * @return 实现类型
     */
    ProviderType providerType();

    /**
     * 把统一消息模型转换为渠道入参，并完成渠道特定的必填校验。
     *
     * @param envelope 统一消息模型
     * @return 渠道入参
     */
    M convert(MessageEnvelope envelope);

    /**
     * 渠道投递：把已转换的渠道消息交给厂商。
     * <p>
     * 与门面 {@link MessageSender#send(MessageEnvelope)} 同名但层次不同：门面负责校验、限流、路由；
     * 本方法只执行一次外部调用，不做任何前置判断，也不改写 {@code messageId}。
     *
     * @param message 渠道入参
     * @return 投递结果
     */
    ChannelResult send(M message);
}
