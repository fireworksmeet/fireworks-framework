package com.yzm.fireworks.common.util;

import lombok.experimental.UtilityClass;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;

/**
 * 框架内部线程池 / 调度器的统一入口。
 *
 * <p><b>只服务框架自己</b>：线程数、队列、拒绝策略由框架写死或来自组件自身配置，
 * <b>不读取</b> {@code spring.task.execution.*} / {@code spring.task.scheduling.*}——
 * 内部基础设施的并发不应该被业务全局配置改变（业务把默认池调成 64 线程、或开启虚拟线程，
 * 框架的轮询池不该跟着变）。
 *
 * <p><b>为什么不用 Boot 的 {@code ThreadPoolTaskExecutorBuilder}</b>：它是容器里的<b>单例</b>且方法可变，
 * Boot 的设计假设是「一个 Builder 产一个池」；框架要产多个池（分道），复用同一个 Builder 会污染它的字段，
 * 影响之后任何使用该 Builder 的代码。
 *
 * <p><b>业务需要自己的线程池时，请按 Boot 官方写法声明 Bean</b>（Spring 会调用 {@code initialize()} 与 {@code destroy()}，
 * 并自动带上容器中的 {@code TaskDecorator} 与 {@code ThreadPoolTaskExecutorCustomizer}）：
 * <pre>{@code
 * @Bean
 * ThreadPoolTaskExecutor orderExecutor(ThreadPoolTaskExecutorBuilder builder) {
 *     ThreadPoolTaskExecutor executor = builder.corePoolSize(8)
 *             .maxPoolSize(8)
 *             .queueCapacity(200)
 *             .threadNamePrefix("order-")
 *             .build();
 *     executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
 *     return executor;
 * }
 * }</pre>
 *
 * <p>本类产出的是普通对象（非 Bean），关闭由创建者负责：作为 Bean 暴露时容器会调用 {@code shutdown()}，
 * 否则需自行调用。
 *
 * @author JYuan
 */
@UtilityClass
public class ThreadPoolUtil {

    /**
     * 框架内部线程池：非守护线程，关闭时等待在途任务。
     *
     * <p><b>{@code coreSize} 与 {@code maxSize} 由调用方决定</b>，本方法不做限制。选择前先看扩容规则：
     * 线程池是「先入队、队列满才扩容」，所以 {@code core < max} 只有在 {@code queueCapacity} 很小
     * （或为 0，即 {@link java.util.concurrent.SynchronousQueue}）时才会真正用上 {@code maxSize}；
     * 配一个大队列再指望扩容，实际并发会一直停在 {@code coreSize}。
     *
     * <p>框架内部的轮询/投递池通常传 {@code core == max == 并发度}：并发上限是可预期的硬约束，
     * 队列满时靠拒绝策略形成背压，而不是继续加线程。
     *
     * @param prefix            线程名前缀
     * @param coreSize          核心线程数
     * @param maxSize           最大线程数（须 ≥ coreSize）
     * @param queueCapacity     队列容量；{@code 0} 表示 {@link java.util.concurrent.SynchronousQueue}
     * @param awaitTermination  关闭时等待在途任务的最长时间
     * @param rejectedHandler   拒绝策略
     * @param taskDecorator     任务装饰器（链路上下文传播），可为 {@code null}
     */
    public static ThreadPoolTaskExecutor executor(String prefix, int coreSize, int maxSize, int queueCapacity,
                                                  Duration awaitTermination,
                                                  RejectedExecutionHandler rejectedHandler,
                                                  TaskDecorator taskDecorator) {
        Assert.isTrue(coreSize > 0, "coreSize 必须大于 0");
        Assert.isTrue(maxSize >= coreSize, "maxSize 不能小于 coreSize");
        Assert.isTrue(queueCapacity >= 0, "queueCapacity 不能为负数");
        Assert.notNull(rejectedHandler, "rejectedHandler 不能为 null");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(Math.max(awaitTermination.toMillis(), 0));
        if (taskDecorator != null) {
            executor.setTaskDecorator(taskDecorator);
        }
        executor.setRejectedExecutionHandler(rejectedHandler);
        executor.initialize();
        return executor;
    }

    /**
     * 创建内部定时调度器：单线程、守护线程、关闭时不中断在途任务。
     *
     * <h2>为什么固定 {@code poolSize = 1}</h2>
     * <ul>
     *   <li>调用方每个实例只挂<b>一个</b>周期任务，没有可并行的对象；</li>
     *   <li>{@code scheduleWithFixedDelay} 本身保证同一任务不会并发（上一轮结束后才重新计时），
     *       单线程只是把这条约束从「任务级」提到「调度器级」，语义更硬；</li>
     *   <li>多线程只会多占线程，并让「两轮是否可能重叠」重新依赖实现细节。</li>
     * </ul>
     *
     * <h2>为什么是守护线程</h2>
     * 轮询是幂等的基础设施：这一轮没跑，下一轮照跑；进程若在「已领取未投递」时被杀，
     * 事件会停在 {@code PROCESSING}，由恢复任务重新投递。因此它不应该在关闭流程遗漏时把 JVM 挂住。
     * <p><b>注意</b>：{@code daemon} 只影响「JVM 退出时是否等待」，<b>不等于</b>自动关闭——
     * 停止仍然依赖调用方显式 {@code shutdown()}。
     *
     * <h2>为什么还要设置关闭等待</h2>
     * {@code ExecutorConfigurationSupport#shutdown()} 在不等待时走的是 {@code shutdownNow()}
     * （直接中断在途任务）；{@code waitForTasksToCompleteOnShutdown} 与 {@code awaitTerminationSeconds}
     * 决定「关闭时是否等在途任务、最多等多久」，与 {@code daemon} 是两件独立的事。
     *
     * <h2>为什么不设 {@code threadNamePrefix}</h2>
     * 一旦指定了 {@code ThreadFactory}，前缀就不再参与命名，线程名由工厂给出（{@code prefix + 序号}）。
     *
     * @param prefix           线程名前缀
     * @param awaitTermination 关闭时等待在途任务的最长时间
     */
    public static ThreadPoolTaskScheduler scheduler(String prefix, Duration awaitTermination) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 每个调用方只挂一个周期任务；单线程同时避免"两轮轮询重叠"
        scheduler.setPoolSize(1);
        // 只设工厂：指定工厂后 threadNamePrefix 不再参与命名
        scheduler.setThreadFactory(daemonThreadFactory(prefix));
        // 关闭语义与 daemon 无关：不设 true 会走 shutdownNow()（直接中断），不设等待时间则 stop() 立即返回
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds((int) Math.max(awaitTermination.toSeconds(), 1));
        // 关闭后不再重新调度周期任务、也不再执行已排队的延迟任务
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 以固定间隔调度任务（上一轮结束后再计时），并对不合法的间隔做下限保护。
     *
     * <p>两个轮询器（投递 / 恢复）共用这一段，避免各自重复写「负值夹到 1ms」的判断。
     *
     * @param initialDelay 首次执行延迟；负数按 0 处理
     * @param delay        两轮之间的间隔；{@code null}、0 或负数按 1ms 处理
     */
    public static void scheduleWithFixedDelay(ThreadPoolTaskScheduler scheduler, Runnable task,
                                              Duration initialDelay, Duration delay) {
        Assert.notNull(scheduler, "scheduler 不能为 null");
        Assert.notNull(task, "task 不能为 null");
        Duration safeInitial = initialDelay == null || initialDelay.isNegative() ? Duration.ZERO : initialDelay;
        Duration safeDelay = delay == null || delay.isNegative() || delay.isZero()
                ? Duration.ofMillis(1)
                : delay;
        scheduler.scheduleWithFixedDelay(task, Instant.now().plus(safeInitial), safeDelay);
    }

    /**
     * 「命名 + 守护」线程工厂：线程名为 {@code prefix + 递增序号}，且 {@code daemon = true}。
     *
     * <p>Spring 的 builder 只能配置前缀，没有 daemon 开关；本方法补上这一点，并通过
     * {@code setThreadFactory} 注入给调度器。
     */
    private static ThreadFactory daemonThreadFactory(String prefix) {
        return Thread.ofPlatform()
                .daemon(true)
                .name(prefix, 1)
                .factory();
    }
}
