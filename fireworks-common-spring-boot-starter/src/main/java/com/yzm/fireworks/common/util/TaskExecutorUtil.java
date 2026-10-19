package com.yzm.fireworks.common.util;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring 线程池（{@link ThreadPoolTaskExecutor}）工具类，只补 {@link ThreadPoolTaskExecutorBuilder} 的两处短板：
 * <ul>
 *     <li>Builder 没有拒绝策略的配置入口，只能构建后单独 {@code setRejectedExecutionHandler}；</li>
 *     <li>Builder 产出的实例尚未 {@code initialize()}，直接使用会抛
 *         {@code IllegalStateException: ThreadPoolTaskExecutor not initialized}。</li>
 * </ul>
 *
 * <p><b>Builder 一律从容器注入</b>（{@code @Bean} 方法形参、构造器注入均可）：它是 Spring Boot 依据
 * {@code spring.task.execution.*}、容器中的 {@code ThreadPoolTaskExecutorCustomizer} 以及容器中唯一的
 * {@link org.springframework.core.task.TaskDecorator} 预先配置好的 Bean。
 * 本类刻意不提供"容器里没有 Builder 就自己 new 一个"的降级：那样不仅要复制 Boot 的整套属性绑定
 * （Boot 新增配置项时不会同步），而且造出来的实例默认值与容器路径完全不同
 * （{@code corePoolSize} 为 {@code 1} 而非 {@code 8}、{@code allowCoreThreadTimeOut} 为 {@code false} 而非 {@code true}），
 * 会让"异常"和"正常"两条路径产出行为不一致的线程池。正确做法是让容器在 Builder 缺失时直接启动失败。
 *
 * <p>用法：
 * <pre>{@code
 * @Bean
 * public ThreadPoolTaskExecutor orderExecutor(ThreadPoolTaskExecutorBuilder builder) {
 *     return TaskExecutorUtil.build(
 *             builder.corePoolSize(8)
 *                     .maxPoolSize(16)
 *                     .queueCapacity(200)
 *                     .threadNamePrefix("order-async-")
 *                     .awaitTermination(true)
 *                     .awaitTerminationPeriod(Duration.ofSeconds(30)),
 *             new ThreadPoolExecutor.CallerRunsPolicy());
 * }
 * }</pre>
 *
 * <p>容器 Builder 的默认值（与 Boot 自带的 {@code applicationTaskExecutor} 完全一致）：
 * <ul>
 *     <li>{@code corePoolSize} = {@code 8}（{@code spring.task.execution.pool.core-size}）</li>
 *     <li>{@code maxPoolSize} = {@link Integer#MAX_VALUE}（{@code ...pool.max-size}）</li>
 *     <li>{@code queueCapacity} = {@link Integer#MAX_VALUE}（{@code ...pool.queue-capacity}）</li>
 *     <li>{@code keepAlive} = {@code 60s}（{@code ...pool.keep-alive}）</li>
 *     <li>{@code allowCoreThreadTimeOut} = {@code true}（{@code ...pool.allow-core-thread-timeout}）</li>
 *     <li>{@code threadNamePrefix} = {@code task-}（{@code ...thread-name-prefix}）</li>
 *     <li>{@code waitForTasksToCompleteOnShutdown} = {@code false}
 *         （{@code spring.task.execution.shutdown.await-termination}）</li>
 *     <li>{@code awaitTerminationMillis} = 未设置，即执行器默认的 {@code 0}
 *         （{@code ...shutdown.await-termination-period}）</li>
 *     <li>{@code acceptTasksAfterContextClose} = {@code false}
 *         （{@code ...pool.shutdown.accept-tasks-after-context-close}）</li>
 *     <li>{@code taskDecorator} = 容器中唯一的 {@link org.springframework.core.task.TaskDecorator}
 *         （本框架由 {@code fireworks-tracing} 注册为 {@code ContextPropagatingTaskDecorator}）；容器中没有则为 {@code null}，
 *         存在多个时会抛 {@code NoUniqueBeanDefinitionException}</li>
 *     <li>{@code customizers} = 容器中所有 {@code ThreadPoolTaskExecutorCustomizer}，
 *         按 {@code Ordered} 顺序应用</li>
 * </ul>
 *
 * <p>其中三处默认值最容易踩坑，建议总是显式覆盖：
 * <ul>
 *     <li><b>{@code queueCapacity} 为 {@link Integer#MAX_VALUE}</b>：线程池的策略是「先入队、队列满才扩容」，
 *         队列几乎不会满，因此只设置 {@code maxPoolSize} 而不收敛队列容量时，
 *         实际并发数会一直停在 {@code corePoolSize}；</li>
 *     <li><b>{@code threadNamePrefix} 为 {@code task-}</b>：与 Boot 的 {@code applicationTaskExecutor} 撞名，
 *         日志和线程 dump 里无法区分两个池的线程，务必显式指定前缀；</li>
 *     <li><b>{@code awaitTermination} 为 {@code false}</b>：{@code shutdown()} 会走
 *         {@code shutdownNow()} 中断在途任务；需要优雅停机时显式设置
 *         {@code awaitTermination(true)} 与 {@code awaitTerminationPeriod(...)}。</li>
 * </ul>
 *
 * <p>生命周期：本类产出的是普通对象，不是容器管理的 Bean，调用方需自行在不再使用时 {@code shutdown()}；
 * 若作为 {@code @Bean} 暴露，则由容器负责关闭（此时 Spring 会再调用一次 {@code initialize()}，
 * 仅替换内部的 {@link ThreadPoolExecutor} 实例，无副作用）。
 *
 * @author JYuan
 */
public final class TaskExecutorUtil {

    private TaskExecutorUtil() {
    }

    /**
     * 构建线程池，沿用 Builder 的默认拒绝策略（{@link ThreadPoolExecutor.AbortPolicy}）。
     *
     * @param builder 从容器注入的 {@link ThreadPoolTaskExecutorBuilder}
     * @return 已完成初始化的线程池
     */
    public static ThreadPoolTaskExecutor build(ThreadPoolTaskExecutorBuilder builder) {
        return build(builder, null);
    }

    /**
     * 构建线程池。
     *
     * @param builder                  从容器注入的 {@link ThreadPoolTaskExecutorBuilder}
     * @param rejectedExecutionHandler 拒绝策略；{@code null} 时沿用 Builder 的默认值
     *                                 （{@link ThreadPoolExecutor.AbortPolicy}）。
     *                                 Builder 未提供拒绝策略配置入口，只能构建后单独设置，故由本方法补齐
     * @return 已完成初始化的线程池
     */
    public static ThreadPoolTaskExecutor build(ThreadPoolTaskExecutorBuilder builder, RejectedExecutionHandler rejectedExecutionHandler) {
        Assert.notNull(builder, "builder 不能为 null");
        ThreadPoolTaskExecutor executor = builder.build();
        if (rejectedExecutionHandler != null) {
            executor.setRejectedExecutionHandler(rejectedExecutionHandler);
        }
        executor.initialize();
        return executor;
    }
}
