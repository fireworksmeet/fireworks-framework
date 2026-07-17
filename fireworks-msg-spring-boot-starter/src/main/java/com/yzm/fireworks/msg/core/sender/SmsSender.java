package com.yzm.fireworks.msg.core.sender;


import com.yzm.fireworks.msg.core.message.MessageResult;
import com.yzm.fireworks.msg.core.message.SmsMessage;

/**
 * 短信推送服务接口
 * @author JYuan
 */
public interface SmsSender extends MessageSender<SmsMessage> {

    int MAX_SEND_COUNT = 200;

    /**
     * 发送短信
     */
    @Override
    MessageResult send(SmsMessage message);
}
