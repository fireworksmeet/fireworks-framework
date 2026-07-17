package com.yzm.fireworks.msg.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.yzm.fireworks.msg.enums.MessagePriority;
import com.yzm.fireworks.msg.enums.MessageStatus;
import com.yzm.fireworks.msg.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息发送记录实体
 *
 * @author JYuan
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("message_record")
public class MessageRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息类型
     */
    private MessageType messageType;

    /**
     * 消息优先级
     */
    private MessagePriority priority;

    /**
     * 平台标识
     */
    private String platform;

    /**
     * 接收用户ID
     */
    private String userId;

    /**
     * 群组ID
     */
    private String groupId;

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
     * 发送状态
     */
    private MessageStatus status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 外部消息ID
     */
    private String externalMessageId;

    /**
     * 重试次数
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
