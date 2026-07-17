package com.yzm.fireworks.msg.exception;

/**
 * 消息推送异常
 * @author JYuan
 */
public class MessagePushException extends RuntimeException {
    
    public MessagePushException(String message) {
        super(message);
    }
    
    public MessagePushException(String message, Throwable cause) {
        super(message, cause);
    }
}
