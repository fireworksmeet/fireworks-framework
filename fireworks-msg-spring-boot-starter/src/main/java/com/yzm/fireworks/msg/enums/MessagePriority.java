package com.yzm.fireworks.msg.enums;

import lombok.Getter;

/**
 * 消息优先级枚举
 * @author JYuan
 */
@Getter
public enum MessagePriority {
    /**
     * 高优先级
     */
    HIGH(3),
    
    /**
     * 中优先级
     */
    MEDIUM(2),
    
    /**
     * 低优先级
     */
    LOW(1);
    
    private final int level;
    
    MessagePriority(int level) {
        this.level = level;
    }

}
