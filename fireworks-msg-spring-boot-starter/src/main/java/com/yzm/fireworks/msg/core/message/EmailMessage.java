package com.yzm.fireworks.msg.core.message;

import com.yzm.fireworks.msg.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 邮件消息实体
 * @author JYuan
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmailMessage extends BaseMessage {

    /**
     * 邮件接收人列表
     */
    private String[] emailTo;

    /**
     * 邮件抄送列表
     */
    private String[] emailCc;

    /**
     * 邮件密送列表
     */
    private String[] emailBcc;

    /**
     * 邮件附件路径列表
     */
    private String[] attachments;

    /**
     * 是否HTML邮件
     */
    private boolean htmlEmail;

    @Override
    public MessageType getMessageType() {
        return MessageType.EMAIL;
    }
}

