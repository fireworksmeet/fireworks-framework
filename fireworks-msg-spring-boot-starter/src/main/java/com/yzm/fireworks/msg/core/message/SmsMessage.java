package com.yzm.fireworks.msg.core.message;

import com.yzm.fireworks.msg.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 短信消息实体
 * @author JYuan
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SmsMessage extends BaseMessage {

    /**
     * 手机号列表
     */
    private String[] phoneNumbers;

    /**
     * 短信签名
     */
    private String smsSign;

    @Override
    public MessageType getMessageType() {
        return MessageType.SMS;
    }
}
