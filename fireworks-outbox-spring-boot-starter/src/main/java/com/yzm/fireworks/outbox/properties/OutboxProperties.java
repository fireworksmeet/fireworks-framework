package com.yzm.fireworks.outbox.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Outbox 配置属性。
 *
 * <pre>{@code
 * fireworks:
 *   outbox:
 *     enabled: true
 *     worker:
 *       enabled: true
 *       batch-size: 100
 *       max-concurrency: 4
 *       queue-capacity: 200
 *       initial-delay: 1s
 *       fixed-delay: 1s
 *     recovery:
 *       enabled: true
 *       interval: 1m
 *       timeout: 5m
 *     retry:
 *       max-retry-count: 20
 *       base-delay: 1s
 *       max-delay: 30m
 *       jitter: 500ms
 * }</pre>
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.outbox")
public class OutboxProperties {

    /**
     * 是否启用 Outbox（默认启用；引入本 starter 即代表需要它）
     */
    private boolean enabled = true;

    /**
     * 链路追踪 ID 在 MDC 中的键名（Micrometer Tracing Brave 默认写入 {@code traceId}）
     */
    private String traceIdKey = "traceId";

    /**
     * 投递 Worker 配置
     */
    private Worker worker = new Worker();

    /**
     * PROCESSING 超时恢复配置
     */
    private Recovery recovery = new Recovery();

    /**
     * 重试配置
     */
    private Retry retry = new Retry();

    /**
     * 分道配置：把指定事件类型交给独立的领取条件与线程池，避免被其他类型的积压拖住。
     * <p>未在此列出的类型由默认道处理；不配置时只有一个默认道，行为与之前一致。
     */
    private List<Lane> lanes = new ArrayList<>();

    /**
     * 分道：每个道独立领取、独立线程池。
     */
    @Data
    public static class Lane {

        /**
         * 道名：用于日志与线程名前缀，必填且唯一
         */
        private String name;

        /**
         * 该道负责的事件类型，必填且不能与其他道重叠
         */
        private List<String> eventTypes = new ArrayList<>();

        /**
         * 覆盖默认道的单批条数；为空时取 {@code worker.batch-size}
         */
        private Integer batchSize;

        /**
         * 覆盖默认道的并发度；为空时取 {@code worker.max-concurrency}
         */
        private Integer maxConcurrency;

        /**
         * 覆盖默认道的队列容量；为空时取 {@code worker.queue-capacity}
         */
        private Integer queueCapacity;
    }

    @Data
    public static class Worker {

        /**
         * 是否启动投递 Worker（关闭后事件只入库、不投递，通常用于测试或灰度）
         */
        private boolean enabled = true;

        /**
         * 单次领取的最大事件数
         */
        private int batchSize = 100;

        /**
         * 投递并发度（Handler 执行线程数）
         */
        private int maxConcurrency = 4;

        /**
         * 投递任务队列容量
         */
        private int queueCapacity = 200;

        /**
         * 应用启动后多久开始第一次领取
         */
        private Duration initialDelay = Duration.ofSeconds(1);

        /**
         * 两轮领取之间的固定间隔
         */
        private Duration fixedDelay = Duration.ofSeconds(1);

        /**
         * 关闭时等待在途 Handler 完成的最长时间
         */
        private Duration shutdownTimeout = Duration.ofSeconds(30);
    }

    @Data
    public static class Recovery {

        /**
         * 是否启用 PROCESSING 超时恢复
         */
        private boolean enabled = true;

        /**
         * 恢复扫描间隔
         */
        private Duration interval = Duration.ofMinutes(1);

        /**
         * PROCESSING 超过该时长仍未结束，视为 Worker 宕机，重置回 PENDING
         * <p><b>必须大于 Handler 的最长执行时间</b>，否则会把正常执行中的事件误判为超时并重复投递
         */
        private Duration timeout = Duration.ofMinutes(5);

        /**
         * 应用启动后多久开始第一次恢复扫描
         */
        private Duration initialDelay = Duration.ofSeconds(10);
    }

    @Data
    public static class Retry {

        /**
         * 最大重试次数，超过后置为 DEAD
         */
        private int maxRetryCount = 20;

        /**
         * 退避基准时长
         */
        private Duration baseDelay = Duration.ofSeconds(1);

        /**
         * 退避上限
         */
        private Duration maxDelay = Duration.ofMinutes(30);

        /**
         * 抖动上限：actualDelay = delay + random(0, jitter)
         */
        private Duration jitter = Duration.ofMillis(500);
    }
}
