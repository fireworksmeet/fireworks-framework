package com.yzm.fireworks.outbox.api;

import org.springframework.util.Assert;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbox 事件处理器注册表。
 * <p>
 * 由容器注入全部 {@link OutboxHandler} 构建；同一个 {@code eventType} 注册多次属于编码错误，
 * 启动即 fail-fast，避免运行期出现「同一事件到底由谁处理」的不确定行为。
 * <p>构建完成后只读；不对外暴露内部映射，注册情况在启动日志中输出。
 *
 * @author JYuan
 */
public class OutboxHandlerRegistry {

    private final Map<String, OutboxHandler> handlers;

    public OutboxHandlerRegistry(List<OutboxHandler> handlerList) {
        Map<String, OutboxHandler> map = new LinkedHashMap<>();
        if (handlerList != null) {
            for (OutboxHandler handler : handlerList) {
                String eventType = handler.eventType();
                Assert.hasText(eventType, () -> "OutboxHandler#eventType() 不能为空: " + handler.getClass().getName());
                OutboxHandler existing = map.putIfAbsent(eventType, handler);
                if (existing != null) {
                    throw new IllegalStateException(
                            "重复的 OutboxHandler eventType [%s]：%s / %s".formatted(
                                    eventType, existing.getClass().getName(), handler.getClass().getName()));
                }
            }
        }
        // 只读视图而非 Map.copyOf：map 是构造器局部变量、不会外泄，无需再复制一次；
        // 且视图保留注册顺序，handlers() 打日志时顺序稳定可复现
        this.handlers = Collections.unmodifiableMap(map);
    }

    /**
     * 按事件类型取处理器，未注册返回 {@code null}（由调用方决定置 DEAD）。
     */
    public OutboxHandler get(String eventType) {
        return handlers.get(eventType);
    }
}
