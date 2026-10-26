package com.yzm.fireworks.outbox.worker;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

/**
 * 一个投递道：独立的事件类型过滤 + 独立的线程池。
 * <p>
 * 默认道（{@code default}）不指定 {@code includeTypes}，但会排除所有已分道的事件类型，
 * 因此每条事件只会被一个道领取。
 *
 * @param name         道名（用于日志与线程名前缀）
 * @param batchSize    单次领取上限
 * @param includeTypes 只领取这些类型；空表示不限
 * @param excludeTypes 排除这些类型；空表示不排除
 * @param executor     该道的投递线程池
 * @author JYuan
 */
public record OutboxLane(String name, int batchSize, List<String> includeTypes,
                         List<String> excludeTypes, ThreadPoolTaskExecutor executor) {

    public OutboxLane {
        includeTypes = includeTypes == null ? List.of() : List.copyOf(includeTypes);
        excludeTypes = excludeTypes == null ? List.of() : List.copyOf(excludeTypes);
    }
}
