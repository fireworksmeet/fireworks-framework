package com.yzm.fireworks.msg.core.sender;


import com.yzm.fireworks.msg.core.message.BaseMessage;
import com.yzm.fireworks.msg.core.message.MessageResult;

/**
 * @author JYuan
 */
public interface MessageSender<T extends BaseMessage> {
    /**
     * 发送消息
     *
     * @param message 消息实体
     * @return 发送结果
     */
    MessageResult send(T message);
}
