package com.yzm.fireworks.outbox.worker;

import com.yzm.fireworks.common.util.ThreadPoolUtil;
import com.yzm.fireworks.outbox.domain.OutboxEvent;
import com.yzm.fireworks.outbox.properties.OutboxProperties;
import com.yzm.fireworks.common.util.UuidUtil;
import com.yzm.fireworks.outbox.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.yzm.fireworks.common.constants.StringPool.UNKNOWN;

/**
 * Outbox 投递 Worker：按「道」领取并并发投递。
 *
 * <p>工作模型：
 * <pre>
 * 定时轮询（单线程调度）
 *   → 依次对每个道 claim(batchSize)  短事务内：SELECT ... FOR UPDATE SKIP LOCKED 领取并置 PROCESSING
 *   → 逐条提交到该道的线程池（maxConcurrency）并发执行 Handler
 *   → Dispatcher 在事务外执行远程调用，回写 SUCCEEDED / PENDING / DEAD
 * </pre>
 *
 * <p><b>为什么分道</b>：单道时领取严格按 {@code id} 顺序，某类事件的积压会让后续事件（如订单）
 * 一直排不到。分道后每道有自己的过滤条件与线程池，互不阻塞；未配置分道时只有一个默认道。
 *
 * <p><b>领取与执行分离</b>：领取是一个独立、原子的短事务；远程调用发生在事务之外。
 * 绝不持有数据库行锁去等待远程响应。
 *
 * <p><b>背压</b>：每个道的线程池使用 {@code CallerRunsPolicy}——池与队列都满时，会由轮询线程
 * 直接执行 Handler，从而暂停后续领取，形成自然背压。
 *
 * <p>由 {@link SmartLifecycle} 驱动：容器刷新末尾启动（此时数据源已就绪，但 Web 端口尚未监听），
 * 关闭时对称停止；不依赖 {@code ApplicationReadyEvent}，也不需要自己维护启动状态。
 *
 * @author JYuan
 */
@Slf4j
public class OutboxWorker implements SmartLifecycle {

    private static final int WORKER_ID_MAX_LENGTH = 128;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxDispatcher dispatcher;
    private final OutboxProperties properties;
    private final List<OutboxLane> lanes;
    private final String workerId;
    private final ThreadPoolTaskScheduler scheduler;
    /**
     * 「唤醒任务已入队」标志：保证同一时刻只有一个唤醒任务在排队
     */
    private final AtomicBoolean wakeUpQueued = new AtomicBoolean(false);
    private volatile boolean running;

    public OutboxWorker(OutboxEventRepository outboxEventRepository,
                        OutboxDispatcher dispatcher,
                        OutboxProperties properties,
                        List<OutboxLane> lanes,
                        String applicationName) {
        if (lanes == null || lanes.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个投递道");
        }
        this.outboxEventRepository = outboxEventRepository;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.lanes = List.copyOf(lanes);
        this.workerId = buildWorkerId(applicationName);
        this.scheduler = ThreadPoolUtil.scheduler("outbox-poll-", Duration.ofSeconds(5));
    }

    @Override
    public void start() {
        // 容器保证 start 只调用一次，这里仅防御刷新/重启导致的重复启动
        if (running) {
            return;
        }
        running = true;

        OutboxProperties.Worker worker = properties.getWorker();
        ThreadPoolUtil.scheduleWithFixedDelay(scheduler, this::pollSafely,
                worker.getInitialDelay(), worker.getFixedDelay());
        log.info("[Outbox] 投递 Worker 已启动 workerId={}, lanes={}, fixedDelay={}ms",
                workerId, lanes.stream().map(OutboxLane::name).toList(), worker.getFixedDelay().toMillis());
    }

    /**
     * 立即触发一轮领取，跳过本次 {@code fixed-delay}。
     *
     * <p>何时调用由业务决定，框架不替业务判断时机。典型用法是在事务的 {@code afterCommit} 里调用：
     * <pre>{@code
     * TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
     *     @Override
     *     public void afterCommit() {
     *         outboxWorker.wakeUp();
     *     }
     * });
     * }</pre>
     * 提交前调用只会白跑一次（行对其它连接不可见），不会出错。
     *
     * <p>仍然是 Worker 去 claim，业务不直接执行 Handler——因此不存在「手动执行 + 轮询执行」的竞争。
     * 短时间内多次调用会被合并，避免批量写入时打爆调度器。
     *
     * <p>唤醒是<b>尽力而为</b>：应用正在停止时会被忽略，未投递的事件仍由轮询与恢复任务兜底。
     */
    public void wakeUp() {
        if (!running || !wakeUpQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            // 单线程调度器，必然排在本轮轮询之后，不会与定时轮询并发领取
            scheduler.execute(() -> {
                // 先复位再轮询：本轮执行期间新写入的事件还能再触发一轮；
                // 若放到轮询之后，轮询慢时到达的写入会被丢弃，要等下一次 fixed-delay
                wakeUpQueued.set(false);
                pollSafely();
            });
        } catch (TaskRejectedException e) {
            wakeUpQueued.set(false);
            log.debug("[Outbox] 唤醒被拒绝（应用正在停止）workerId={}", workerId);
        }
    }

    private void pollSafely() {
        for (OutboxLane lane : lanes) {
            try {
                poll(lane);
            } catch (Exception e) {
                // 单个道异常不能影响其它道；Error（OOM 等）不吞，交给线程池默认处理
                log.error("[Outbox] 轮询异常 lane={}，已忽略本次", lane.name(), e);
            }
        }
    }

    private void poll(OutboxLane lane) {
        // 只领「当前能立刻开始处理」的条数：领了却排在队列里，会白白消耗 recovery.timeout 的预算
        int limit = Math.min(lane.batchSize(), availableCapacity(lane.executor()));
        if (limit <= 0) {
            return;
        }
        List<OutboxEvent> events = outboxEventRepository.claim(workerId, limit,
                lane.includeTypes(), lane.excludeTypes());
        if (events == null || events.isEmpty()) {
            return;
        }
        for (OutboxEvent event : events) {
            try {
                lane.executor().execute(() -> dispatcher.dispatch(event));
            } catch (TaskRejectedException ex) {
                // 正常不会发生（CallerRunsPolicy）；仅在线程池已关闭时抛出
                log.warn("[Outbox] 投递任务被拒绝 lane={}, eventId={}，本次跳过",
                        lane.name(), event.getEventId(), ex);
            }
        }
    }

    /**
     * 当前线程池还能立刻接纳多少任务：空闲线程 + 队列剩余容量。
     */
    private int availableCapacity(ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        return (pool.getMaximumPoolSize() - pool.getActiveCount()) + pool.getQueue().remainingCapacity();
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        log.info("[Outbox] 投递 Worker 正在停止 workerId={}", workerId);
        // 先停领取，再让各道线程池按 awaitTermination 等待在途任务
        scheduler.shutdown();
        for (OutboxLane lane : lanes) {
            lane.executor().shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private String buildWorkerId(String applicationName) {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = UNKNOWN;
        }
        String workerId = applicationName + "-" + host + "-" + UuidUtil.shortUuid(8);
        return workerId.length() > WORKER_ID_MAX_LENGTH
                ? workerId.substring(0, WORKER_ID_MAX_LENGTH)
                : workerId;
    }
}
