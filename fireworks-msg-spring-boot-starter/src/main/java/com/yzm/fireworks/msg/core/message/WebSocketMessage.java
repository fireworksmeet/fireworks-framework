package com.yzm.fireworks.msg.core.message;

import com.yzm.fireworks.msg.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * WebSocket消息实体
 *
 * @author JYuan
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WebSocketMessage extends BaseMessage {

    /**
     * WebSocket频道路径
     */
    private String channelPath;

    /**
     * 接收用户ID列表
     */
    private String[] userIds;

    /**
     * 群组ID
     */
    private String groupId;

    /**
     * 负载
     */
    private Object payload;

    @Override
    public MessageType getMessageType() {
        return MessageType.WEBSOCKET;
    }
}
