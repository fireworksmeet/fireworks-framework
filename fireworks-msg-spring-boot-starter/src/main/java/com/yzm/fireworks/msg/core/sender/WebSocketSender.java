package com.yzm.fireworks.msg.core.sender;

import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.msg.core.message.MessageResult;
import com.yzm.fireworks.msg.core.message.WebSocketMessage;
import com.yzm.fireworks.msg.properties.MessageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static com.yzm.fireworks.common.constants.StringPool.COMMA;
import static com.yzm.fireworks.common.constants.StringPool.SLASH;

/**
 * WebSocket推送服务（基于Nchan）
 *
 * @author JYuan
 */
@Slf4j
public class WebSocketSender implements MessageSender<WebSocketMessage> {

    private final RestClient restClient;
    private final MessageProperties properties;

    public WebSocketSender(MessageProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getWebsocket().getNchanUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 发送WebSocket消息
     */
    @Override
    public MessageResult send(WebSocketMessage message) {
        try {
            String channelPath = buildChannelPath(message);

            // 发布到Nchan
            restClient.post()
                    .uri(channelPath)
                    .body(buildPushContent(message))
                    .retrieve()
                    .toBodilessEntity();

            log.info("WebSocket message sent successfully: messageId={}, channel={}",
                    message.getMessageId(), channelPath);

            return MessageResult.success(message.getMessageId());

        } catch (Exception e) {
            log.error("Failed to send WebSocket message: messageId={}", message.getMessageId(), e);
            return MessageResult.failed(message.getMessageId(), e.getMessage());
        }
    }

    /**
     * 构建频道路径
     */
    private String buildChannelPath(WebSocketMessage message) {
        String publishPrefix = properties.getWebsocket().getPublishPrefix();

        // 如果消息已指定频道路径，直接使用
        String channelPath = message.getChannelPath();
        if (StringUtils.hasText(channelPath)) {
            return channelPath.startsWith(SLASH) ? publishPrefix + channelPath : publishPrefix + SLASH + channelPath;
        }

        // 根据消息属性构建频道路径
        String platform = StringUtils.hasText(message.getPlatform()) ? message.getPlatform() : "default";

        String[] userIds = message.getUserIds();
        if (!ObjectUtils.isEmpty(userIds)) {
            // 用户私有频道
            return String.format("%s/%s/%s", publishPrefix, platform, buildUserIds(userIds));
        } else if (StringUtils.hasText(message.getGroupId())) {
            // 群组频道
            return String.format("%s/%s/%s", publishPrefix, platform, message.getGroupId());
        } else {
            // 广播频道
            return String.format("%s/%s/broadcast", publishPrefix, platform);
        }
    }

    private String buildUserIds(String[] userIds) {
        if (ObjectUtils.isEmpty(userIds)) {
            return StringPool.EMPTY;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < userIds.length; i++) {
            if (i > 0) {
                sb.append(COMMA);
            }
            sb.append(StringUtils.hasText(userIds[i]) ? userIds[i] : StringPool.EMPTY);
        }

        String result = sb.toString();
        long byteLength = result.getBytes(StandardCharsets.UTF_8).length;

        int nchanMaxChannelIdLength = properties.getWebsocket().getNchanMaxChannelIdLength();
        if (byteLength > nchanMaxChannelIdLength) {
            throw new IllegalArgumentException(
                    String.format("Concatenated user ID string in WebSocket message exceeds maximum length %d bytes", nchanMaxChannelIdLength)
            );
        }

        return result;
    }

    /**
     * 构建推送内容
     */
    private Object buildPushContent(WebSocketMessage message) {
        return new WebSocketPayload(
                message.getMessageId(),
                message.getTitle(),
                message.getContent(),
                message.getPayload()
        );
    }

    /**
     * WebSocket消息格式
     */
    private record WebSocketPayload(
            String messageId,
            String title,
            String content,
            Object data
    ) {
    }
}
