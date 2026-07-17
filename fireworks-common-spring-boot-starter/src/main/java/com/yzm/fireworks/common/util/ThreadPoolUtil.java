package com.yzm.fireworks.common.util;

import com.yzm.fireworks.common.decorator.MdcTaskDecorator;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ObjectUtils;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author JYuan
 */
public class ThreadPoolUtil {

    public static ThreadPoolTaskExecutor createThreadPoolTaskExecutor(String threadNamePrefix) {
        return createThreadPoolTaskExecutor(threadNamePrefix, new ThreadPoolExecutor.CallerRunsPolicy(), new MdcTaskDecorator());
    }

    public static ThreadPoolTaskExecutor createThreadPoolTaskExecutor(String threadNamePrefix, RejectedExecutionHandler rejectedExecutionHandler, TaskDecorator taskDecorator) {
        return createThreadPoolTaskExecutor(
                Runtime.getRuntime().availableProcessors() + 1,
                Runtime.getRuntime().availableProcessors() << 1,
                300,
                500, threadNamePrefix, rejectedExecutionHandler, 60, taskDecorator);
    }

    /**
     * 创建Spring线程池
     *
     * @param corePoolSize             核心线程数
     * @param maxPoolSize              最大线程数
     * @param keepAliveSeconds         救急线程的空闲时间
     * @param queueCapacity            队列容量
     * @param threadNamePrefix         线程名前缀
     * @param rejectedExecutionHandler 拒绝策略
     * @param awaitTerminationSeconds  等待任务完成的最长时间（秒），超时后强制停止
     * @param taskDecorator            线程池的装饰器
     * @return ThreadPoolTaskExecutor
     */
    public static ThreadPoolTaskExecutor createThreadPoolTaskExecutor(int corePoolSize, int maxPoolSize, int keepAliveSeconds, int queueCapacity, String threadNamePrefix, RejectedExecutionHandler rejectedExecutionHandler, int awaitTerminationSeconds, TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(corePoolSize);
        threadPoolTaskExecutor.setMaxPoolSize(maxPoolSize);
        threadPoolTaskExecutor.setQueueCapacity(queueCapacity);
        threadPoolTaskExecutor.setThreadNamePrefix(threadNamePrefix);
        threadPoolTaskExecutor.setKeepAliveSeconds(keepAliveSeconds);
        threadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        threadPoolTaskExecutor.setRejectedExecutionHandler(rejectedExecutionHandler);
        threadPoolTaskExecutor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        if (!ObjectUtils.isEmpty(taskDecorator)) {
            threadPoolTaskExecutor.setTaskDecorator(taskDecorator);
        }
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor;
    }
}
