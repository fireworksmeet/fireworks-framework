package com.yzm.fireworks.msg.core.message;

import com.yzm.fireworks.msg.enums.MessagePriority;
import com.yzm.fireworks.msg.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息基类 - 包含所有消息类型共享的字段
 *
 * @author JYuan
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseMessage implements Serializable {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息优先级
     */
    @Builder.Default
    private MessagePriority priority = MessagePriority.LOW;

    /**
     * 平台标识
     */
    @Builder.Default
    private String platform = "default";

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 模板ID
     */
    private String templateId;

    /**
     * 模板参数
     */
    private Map<String, Object> templateParams;

    /**
     * 定时发送时间
     */
    private LocalDateTime scheduledTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否需要去重
     */
    private boolean needDeduplication;

    /**
     * 获取消息类型
     */
    public abstract MessageType getMessageType();
}
