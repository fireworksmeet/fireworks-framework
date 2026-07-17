package com.yzm.fireworks.common.decorator;

import io.micrometer.context.ContextSnapshot;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

/**
 * MDC 与 Tracing 上下文传递装饰器
 * <p>用于在异步线程池中传递 MDC 和 Micrometer Tracing 上下文（如 traceId、span 等）
 *
 * @author JYuan
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // 捕获当前线程的所有上下文（包括 MDC、Observation/Tracing Span 等）
        // 并包装 runnable，使其在子线程中能够恢复和清理这些上下文
        return ContextSnapshot.captureAll().wrap(runnable);
    }
}
