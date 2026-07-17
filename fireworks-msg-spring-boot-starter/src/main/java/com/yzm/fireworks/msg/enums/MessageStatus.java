package com.yzm.fireworks.msg.enums;

/**
 * 消息发送状态
 * @author JYuan
 */
public enum MessageStatus {
    /**
     * 待发送
     */
    PENDING,
    
    /**
     * 发送中
     */
    SENDING,
    
    /**
     * 发送成功
     */
    SUCCESS,
    
    /**
     * 发送失败
     */
    FAILED,
    
    /**
     * 已取消
     */
    CANCELLED
}
