package com.yzm.fireworks.msg.service;

import com.yzm.fireworks.msg.core.message.*;
import com.yzm.fireworks.msg.core.sender.EmailSender;
import com.yzm.fireworks.msg.core.sender.SmsSender;
import com.yzm.fireworks.msg.core.sender.WebSocketSender;
import com.yzm.fireworks.msg.enums.MessageType;
import com.yzm.fireworks.msg.exception.MessagePushException;
import com.yzm.fireworks.msg.properties.MessageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * 消息路由服务 - 智能路由和失败降级
 *
 * @author JYuan
 */
@Slf4j
public class MessageRouterService {

    private final MessageProperties properties;
    private final WebSocketSender webSocketSender;
    private final SmsSender smsSender;
    private final EmailSender emailSender;


    public MessageRouterService(MessageProperties properties, @Nullable WebSocketSender webSocketSender, @Nullable SmsSender smsSender, @Nullable EmailSender emailSender) {
        this.properties = properties;
        this.webSocketSender = webSocketSender;
        this.smsSender = smsSender;
        this.emailSender = emailSender;
    }

    /**
     * 路由基础消息（根据类型分发）
     */
    public MessageResult route(BaseMessage message) {
        MessageType messageType = message.getMessageType();

        if (ObjectUtils.isEmpty(messageType)) {
            throw new MessagePushException("Message type is required");
        }

        return switch (messageType) {
            case WEBSOCKET -> {
                if (isConfiguredWebSocket()) {
                    if (message instanceof WebSocketMessage wsMsg) {
                        yield webSocketSender.send(wsMsg);
                    }
                    throw new MessagePushException("Invalid message type for WebSocket");
                }
                yield MessageResult.failed(message.getMessageId(), "WebSocket sender not configured");
            }
            case SMS -> {
                if (isConfiguredSms()) {
                    if (message instanceof SmsMessage smsMsg) {
                        yield smsSender.send(smsMsg);
                    }
                    throw new MessagePushException("Invalid message type for SMS");
                }
                yield MessageResult.failed(message.getMessageId(), "SMS sender not configured");
            }
            case EMAIL -> {
                if (isConfiguredEmail()) {
                    if (message instanceof EmailMessage emailMsg) {
                        yield emailSender.send(emailMsg);
                    }
                    throw new MessagePushException("Invalid message type for Email");
                }
                yield MessageResult.failed(message.getMessageId(), "Email sender not configured");
            }
        };
    }

    /**
     * 检查是否可以通过WebSocket发送
     */
    private boolean isConfiguredWebSocket() {
        return properties.getWebsocket().isEnabled() && !ObjectUtils.isEmpty(webSocketSender);
    }

    /**
     * 检查是否可以通过短信发送
     */
    private boolean isConfiguredSms() {
        return properties.getSms().isEnabled() && !ObjectUtils.isEmpty(smsSender);
    }

    /**
     * 检查是否可以通过邮件发送
     */
    private boolean isConfiguredEmail() {
        return properties.getEmail().isEnabled() && !ObjectUtils.isEmpty(emailSender);
    }
}
