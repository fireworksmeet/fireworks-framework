package com.yzm.fireworks.message.api;

import com.yzm.fireworks.message.domain.ChannelResult;
import com.yzm.fireworks.message.domain.MessageEnvelope;

/**
 * 发送事件钩子。
 * <p>
 * 用于业务落库、审计与指标；回调异常只记录日志，不影响发送结果。
 *
 * @author JYuan
 */
public interface MessageListener {

    /**
     * 发送成功。
     *
     * @param envelope 消息
     * @param result   结果
     */
    default void onSuccess(MessageEnvelope envelope, ChannelResult result) {
    }

    /**
     * 发送失败。
     *
     * @param envelope 消息
     * @param result   结果
     */
    default void onFailure(MessageEnvelope envelope, ChannelResult result) {
    }
}
