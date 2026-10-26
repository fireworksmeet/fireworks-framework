package com.yzm.fireworks.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.outbox.api.OutboxAppendRequest;
import com.yzm.fireworks.outbox.api.OutboxService;
import com.yzm.fireworks.outbox.domain.OutboxEvent;
import com.yzm.fireworks.outbox.exception.OutboxException;
import com.yzm.fireworks.outbox.properties.OutboxProperties;
import com.yzm.fireworks.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link OutboxService} 默认实现。
 *
 * <p>只做三件事：生成 eventId、把 payload 序列化成 JSON、写一行 outbox_event。
 * 不发起任何远程调用，也不主动开事务——它加入调用方（业务方法）已有的本地事务。
 *
 * @author JYuan
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultOutboxService implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;

    @Override
    public String append(String eventType, Object payload) {
        return append(eventType, null, null, payload);
    }

    @Override
    public String append(String eventType, String entityType, String entityId, Object payload) {
        return append(OutboxAppendRequest.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .payload(payload)
                .build());
    }

    @Override
    public String append(OutboxAppendRequest request) {
        Assert.notNull(request, "OutboxAppendRequest 不能为空");
        Assert.hasText(request.getEventType(), "outbox eventType 不能为空");
        Assert.notNull(request.getPayload(), "outbox payload 不能为空");

        String eventId = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();

        OutboxEvent event = OutboxEvent.builder()
                .eventId(eventId)
                .eventType(request.getEventType())
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .payload(toJson(request.getPayload()))
                .status(10)
                .retryCount(0)
                .maxRetryCount(request.getMaxRetryCount() != null
                        ? request.getMaxRetryCount()
                        : properties.getRetry().getMaxRetryCount())
                .nextRetryAt(now)
                .traceId(resolveTraceId(request))
                .createdAt(now)
                .updatedAt(now)
                .build();

        outboxEventRepository.insert(event);
        log.debug("[Outbox] 已追加事件 eventId={}, eventType={}, entityType={}, entityId={}",
                eventId, event.getEventType(), event.getEntityType(), event.getEntityId());
        return eventId;
    }

    /**
     * 载荷序列化：字符串视为「已是 JSON」直接落库，其余对象走 Jackson。
     */
    private String toJson(Object payload) {
        if (payload instanceof String json) {
            return json;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new OutboxException("outbox payload 序列化失败: " + payload.getClass().getName(), e);
        }
    }

    /**
     * 链路追踪 ID：显式指定优先，否则从 MDC 读取（Micrometer Tracing 默认键为 traceId）。
     */
    private String resolveTraceId(OutboxAppendRequest request) {
        if (StringUtils.hasText(request.getTraceId())) {
            return request.getTraceId();
        }
        return MDC.get(properties.getTraceIdKey());
    }
}
