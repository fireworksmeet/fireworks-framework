package com.yzm.fireworks.outbox.worker;

import com.yzm.fireworks.common.util.ThreadPoolUtil;
import com.yzm.fireworks.outbox.properties.OutboxProperties;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 根据配置构造投递道。
 * <p>
 * 规则：兜底道负责所有「未分道」的类型；每个 {@code lanes} 配置项一个道。
 * 道名重复（含占用保留名 {@code default}）、事件类型重复分道、{@code event-types} 为空都会在启动期失败。
 *
 * @author JYuan
 */
public final class OutboxLaneFactory {

    private OutboxLaneFactory() {
    }

    /**
     * 按配置构造投递道；配置非法时在启动期抛 {@link IllegalArgumentException}。
     */
    public static List<OutboxLane> build(OutboxProperties properties, TaskDecorator taskDecorator) {
        OutboxProperties.Worker worker = properties.getWorker();
        Set<String> assignedTypes = new LinkedHashSet<>();
        Set<String> laneNames = new HashSet<>();

        for (OutboxProperties.Lane lane : properties.getLanes()) {
            Assert.hasText(lane.getName(), "fireworks.outbox.lanes 存在未命名的道");
            Assert.isTrue(!OutboxLane.DEFAULT_LANE_NAME.equals(lane.getName()),
                    () -> "道名 '" + OutboxLane.DEFAULT_LANE_NAME + "' 由框架保留（兜底道），请改用其它名称");
            Assert.isTrue(laneNames.add(lane.getName()), () -> "道名重复: " + lane.getName());
            Assert.notEmpty(lane.getEventTypes(), () -> "道 " + lane.getName() + " 未配置 event-types");
            for (String eventType : lane.getEventTypes()) {
                Assert.isTrue(assignedTypes.add(eventType), () -> "事件类型被重复分道: " + eventType);
            }
        }

        List<OutboxLane> lanes = new ArrayList<>();
        // 兜底道：领取所有「未分道」的类型
        lanes.add(new OutboxLane(OutboxLane.DEFAULT_LANE_NAME,
                Math.max(worker.getBatchSize(), 1),
                List.of(),
                List.copyOf(assignedTypes),
                executor("outbox-worker-" + OutboxLane.DEFAULT_LANE_NAME + "-",
                        Math.max(worker.getMaxConcurrency(), 1),
                        Math.max(worker.getQueueCapacity(), 1), worker, taskDecorator)));

        for (OutboxProperties.Lane lane : properties.getLanes()) {
            int concurrency = lane.getMaxConcurrency() != null
                    ? Math.max(lane.getMaxConcurrency(), 1)
                    : Math.max(worker.getMaxConcurrency(), 1);
            int queueCapacity = lane.getQueueCapacity() != null
                    ? Math.max(lane.getQueueCapacity(), 1)
                    : Math.max(worker.getQueueCapacity(), 1);
            int batchSize = lane.getBatchSize() != null
                    ? Math.max(lane.getBatchSize(), 1)
                    : Math.max(worker.getBatchSize(), 1);
            lanes.add(new OutboxLane(lane.getName(), batchSize,
                    List.copyOf(lane.getEventTypes()), List.of(),
                    executor("outbox-worker-" + lane.getName() + "-", concurrency, queueCapacity, worker, taskDecorator)));
        }
        return lanes;
    }

    /**
     * 每个道一个线程池：核心 = 最大 = 并发度，队列满时 CallerRunsPolicy 形成背压。
     * <p>内部池不读 {@code spring.task.execution.*}：并发由分道配置决定。
     */
    private static ThreadPoolTaskExecutor executor(String prefix, int concurrency, int queueCapacity,
                                                   OutboxProperties.Worker worker, TaskDecorator taskDecorator) {
        // 核心 = 最大：并发上限是硬约束，队列满时靠 CallerRunsPolicy 形成背压
        return ThreadPoolUtil.executor(prefix, concurrency, concurrency, queueCapacity,
                worker.getShutdownTimeout(), new ThreadPoolExecutor.CallerRunsPolicy(), taskDecorator);
    }
}
