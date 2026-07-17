package com.yzm.fireworks.msg.core.message;

import com.yzm.fireworks.msg.enums.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息发送结果
 * @author JYuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResult implements Serializable {
    
    /**
     * 消息ID
     */
    private String messageId;
    
    /**
     * 发送状态
     */
    private MessageStatus status;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 发送时间
     */
    @Builder.Default
    private LocalDateTime sendTime = LocalDateTime.now();
    
    /**
     * 外部系统消息ID（如短信回执ID）
     */
    private String externalMessageId;
    
    /**
     * 重试次数
     */
    @Builder.Default
    private int retryCount = 0;
    
    public static MessageResult success(String messageId) {
        return MessageResult.builder()
                .messageId(messageId)
                .status(MessageStatus.SUCCESS)
                .success(true)
                .build();
    }
    
    public static MessageResult failed(String messageId, String errorMessage) {
        return MessageResult.builder()
                .messageId(messageId)
                .status(MessageStatus.FAILED)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
