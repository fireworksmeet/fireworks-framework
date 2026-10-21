package com.yzm.fireworks.message.channel.realtime;

import com.yzm.fireworks.message.domain.ChannelResult;
import com.yzm.fireworks.message.domain.MessageEnvelope;
import com.yzm.fireworks.message.domain.ProviderType;
import com.yzm.fireworks.message.exception.MessageValidationException;
import com.yzm.fireworks.message.properties.MessageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.yzm.fireworks.common.constants.StringPool.HTTP_HOST_PREFIX;
import static com.yzm.fireworks.common.constants.StringPool.HTTPS_HOST_PREFIX;
import static com.yzm.fireworks.common.constants.StringPool.NETWORK_ERROR;

/**
 * Nchan 实时推送实现。
 * <p>
 * 本质是一次 HTTP POST：业务给出 {@code path} 与频道 ID 列表，框架只负责「拼 URL + 按上限分批 + 发送」，
 * 不理解平台、用户、群组等频道语义。
 * <p>
 * 复用注入的 {@link RestClient.Builder}：connect / read 超时来自 {@code spring.http.client.*}，连接池来自
 * {@code fireworks.rest-client.*}，此处不覆盖 {@code requestFactory}——覆盖会连带丢弃连接池与保活配置。
 *
 * @author JYuan
 */
@Slf4j
public class NchanProvider implements RealtimeProvider {

    private final RestClient restClient;

    private final MessageProperties.Nchan config;

    /**
     * 频道 ID 分隔符；必须与 Nginx 的 {@code nchan_channel_id_split_delimiter} 一致。
     */
    private final String delimiter;

    public NchanProvider(MessageProperties properties, RestClient.Builder restClientBuilder) {
        this.config = properties.getChannels().getRealtime().getNchan();
        this.delimiter = validDelimiter(config.getChannelIdDelimiter());
        this.restClient = restClientBuilder.build();
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.NCHAN;
    }

    @Override
    public RealtimeMessage convert(MessageEnvelope envelope) {
        if (envelope.getRecipient() == null || CollectionUtils.isEmpty(envelope.getRecipient().getChannelIds())) {
            throw new MessageValidationException("recipient.channelIds is required for REALTIME");
        }
        String path = envelope.getRealtimePath();
        if (!StringUtils.hasText(path)) {
            throw new MessageValidationException("path is required for REALTIME");
        }

        return RealtimeMessage.builder()
                .messageId(envelope.getMessageId())
                .path(path.trim())
                .channelIds(envelope.getRecipient().getChannelIds())
                .title(envelope.getTitle())
                .content(envelope.getContent())
                .data(envelope.getRealtimeData())
                .build();
    }

    @Override
    public ChannelResult send(RealtimeMessage message) {
        List<URI> targets;
        try {
            targets = buildTargets(message);
        } catch (MessageValidationException e) {
            return new ChannelResult.PermanentFailure(message.getMessageId(), "INVALID_CHANNEL", e.getMessage());
        }

        NchanPayload payload = new NchanPayload(
                message.getMessageId(),
                message.getTitle(),
                message.getContent(),
                message.getData(),
                Instant.now().toString());

        for (URI target : targets) {
            try {
                restClient.post()
                        .uri(target)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                log.warn("Nchan publish failed: messageId={}, uri={}, status={}, body={}",
                        message.getMessageId(), target, status, e.getResponseBodyAsString());
                if (status == 408 || status == 429 || status >= 500) {
                    return new ChannelResult.RetryableFailure(
                            message.getMessageId(), "GATEWAY_" + status, e.getMessage(), e);
                }
                return new ChannelResult.PermanentFailure(
                        message.getMessageId(), "GATEWAY_" + status, e.getMessage());
            } catch (ResourceAccessException e) {
                return new ChannelResult.RetryableFailure(
                        message.getMessageId(), NETWORK_ERROR, e.getMessage(), e);
            }
        }

        return new ChannelResult.Success(message.getMessageId(), null);
    }

    /**
     * 解析出每次 publish 的目标 URI：路径不变，频道 ID 分批后逗号拼接。
     */
    private List<URI> buildTargets(RealtimeMessage message) {
        String path = message.getPath();
        validatePath(path);

        List<URI> targets = new ArrayList<>();
        for (String batch : batches(message.getChannelIds())) {
            targets.add(URI.create(join(path, batch)));
        }
        return targets;
    }

    /**
     * 路径校验：不含空白，协议只能是 http / https（或无协议）。
     */
    private void validatePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new MessageValidationException("path is required for REALTIME");
        }
        if (path.chars().anyMatch(Character::isWhitespace)) {
            throw new MessageValidationException("path must not contain whitespace: " + path);
        }
        if (!hasHttpScheme(path) && path.contains("://")) {
            throw new MessageValidationException("only http/https path is supported: " + path);
        }
    }

    /**
     * 频道 ID 分批：受单批字节数与单批频道数双重限制。
     */
    private List<String> batches(List<String> channelIds) {
        if (CollectionUtils.isEmpty(channelIds)) {
            throw new MessageValidationException("channelIds is required for REALTIME");
        }

        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;
        int channelsInBatch = 0;

        for (String channelId : channelIds) {
            validateChannelId(channelId);

            int channelBytes = channelId.getBytes(StandardCharsets.UTF_8).length;
            if (channelsInBatch == 0 && channelBytes > config.getMaxChannelPathBytes()) {
                throw new MessageValidationException("channel path exceeds max bytes: " + channelId);
            }

            int extraBytes = channelsInBatch == 0 ? channelBytes : channelBytes + 1;
            boolean exceedsBytes = channelsInBatch > 0
                    && currentBytes + extraBytes > config.getMaxChannelPathBytes();
            if (exceedsBytes || channelsInBatch >= config.getMaxChannelsPerPublish()) {
                batches.add(current.toString());
                current.setLength(0);
                currentBytes = 0;
                channelsInBatch = 0;
            }

            if (channelsInBatch > 0) {
                current.append(delimiter);
                currentBytes += delimiter.length();
            }
            current.append(channelId);
            currentBytes += channelBytes;
            channelsInBatch++;
        }

        if (channelsInBatch > 0) {
            batches.add(current.toString());
        }
        return batches;
    }

    private void validateChannelId(String channelId) {
        if (!StringUtils.hasText(channelId)) {
            throw new MessageValidationException("channelId must not be blank");
        }
        if (channelId.contains(delimiter)) {
            throw new MessageValidationException(
                    "channelId must not contain delimiter '" + delimiter + "': " + channelId);
        }
        if (channelId.chars().anyMatch(Character::isWhitespace)) {
            throw new MessageValidationException("channelId must not contain whitespace: " + channelId);
        }
    }

    /**
     * 分隔符必须是单个可打印 ASCII 字符：Nginx 只接受单字符，且字节数按 1 计。
     */
    private static String validDelimiter(String value) {
        if (value == null || value.length() != 1 || value.charAt(0) < '!' || value.charAt(0) > '~') {
            throw new IllegalStateException(
                    "fireworks.message.channels.realtime.nchan.channel-id-delimiter must be a single printable "
                            + "ASCII character, but was: " + value);
        }
        return value;
    }

    /**
     * 拼接 publish 地址：绝对 URL 优先，相对路径拼在 {@code url} 之后。
     */
    private String join(String path, String batch) {
        String base = hasHttpScheme(path)
                ? path
                : stripTrailingSlashes(config.getUrl()) + "/" + stripSlashes(path);
        return stripTrailingSlashes(base) + "/" + batch;
    }

    /**
     * 是否带 http / https 协议，大小写不敏感。
     */
    private static boolean hasHttpScheme(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith(HTTP_HOST_PREFIX) || lower.startsWith(HTTPS_HOST_PREFIX);
    }

    private static String stripTrailingSlashes(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String stripSlashes(String value) {
        return stripTrailingSlashes(value).replaceFirst("^/+", "");
    }

    /**
     * Nchan publish 消息体，字段名即客户端看到的 JSON 结构。
     * <p>
     * {@code timestamp} 用 ISO-8601 字符串而没有用 {@link Instant}：JSON 由宿主应用的
     * {@code ObjectMapper} 序列化（RestClient 的转换器取自应用上下文），日期格式会随其配置变化
     * （写成时间戳数字、纳秒位数、全局日期格式），而这是发给客户端的固定契约，不能跟着宿主配置漂移。
     */
    private record NchanPayload(String messageId,
                                String title,
                                String content,
                                Object data,
                                String timestamp) {
    }
}
