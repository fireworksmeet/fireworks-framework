package com.yzm.fireworks.tracing;

import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * 链路追踪的自动配置：注册异步上下文传播装饰器 + 启动诊断。
 *
 * <p><b>为什么装饰器放在这里，而不是 {@code fireworks-common}</b>：{@link ContextPropagatingTaskDecorator}
 * 本身只是把 <b>已注册的</b> {@code ThreadLocalAccessor} 捕获/还原，自己不提供任何 accessor。
 * 当前 classpath 上唯一自动注册的 accessor 是 {@code ObservationThreadLocalAccessor}，它随
 * {@code micrometer-observation} 进入——而 {@code micrometer-observation} 只由本模块带进来。
 * 放在 common 的后果是：装饰器 Bean 存在，但 accessor 列表为空，异步任务什么也拿不到，
 * 属于“装了但不生效”的假能力。
 *
 * <p><b>关于条件判断</b>：本类直接引用了 {@link Tracer} 与 {@link SpanExporter} 两个类型，
 * 因此 {@code @ConditionalOnClass} 必须**同时**包含它们——只判 {@code Tracer} 时，
 * 业务用 brave bridge 或排除 OTel SDK 会让条件通过、类加载失败（报错与“追踪”无关，极难定位）。
 *
 * <p>启动诊断的作用：链路追踪属于典型的「静默失败」，日志格式里的 {@code %X{traceId:-}} 取不到值时只会
 * 渲染成空，不会有任何报错，业务方往往在排查问题时才后知后觉地发现整条链路上没有 traceId。
 * 因此在启动完成时显式打出一行状态，把「是否启用、Tracer 是否装配、有没有 exporter 在上报」说清楚。
 *
 * <p>注意这里<b>不打印采样率，除非真的存在 exporter</b>：采样率只决定 span 是否被记录并上报，
 * 没有 exporter 时它不产生任何可观测结果，报出来只会误导。
 *
 * @author JYuan
 */
@AutoConfiguration
@ConditionalOnClass({Tracer.class, SpanExporter.class})
@ConditionalOnProperty(prefix = "fireworks.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TracingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TracingAutoConfiguration.class);

    private static final String PREFIX = "[fireworks-tracing] ";

    private final Environment environment;

    private final ObjectProvider<Tracer> tracerProvider;

    private final ObjectProvider<SpanExporter> spanExporterProvider;

    public TracingAutoConfiguration(Environment environment,
                                    ObjectProvider<Tracer> tracerProvider,
                                    ObjectProvider<SpanExporter> spanExporterProvider) {
        this.environment = environment;
        this.tracerProvider = tracerProvider;
        this.spanExporterProvider = spanExporterProvider;
    }

    /**
     * 注册异步上下文传播装饰器。
     *
     * <p>Spring Boot 的 {@code applicationTaskExecutor}（{@code @Async} 的默认线程池）以及
     * {@code ThreadPoolTaskExecutorBuilder} 都会自动应用容器中唯一的 {@link TaskDecorator}，
     * 因此注册该 Bean 后，异步任务即可继承提交线程的 Observation / TraceContext。
     *
     * <p>必须保证容器中只存在一个 {@link TaskDecorator}：Boot 内部通过
     * {@code ObjectProvider#getIfAvailable} 获取，出现多个会直接抛 {@code NoUniqueBeanDefinitionException}。
     * 业务若自行定义了 {@link TaskDecorator}，这里让位（{@code @ConditionalOnMissingBean}）。
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator(ContextSnapshotFactory.builder().build());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void tracingStartupDiagnostics() {
        if (!environment.getProperty("management.tracing.enabled", Boolean.class, true)) {
            log.warn(PREFIX + "链路追踪已关闭（management.tracing.enabled=false），日志中不会出现 traceId");
            return;
        }
        if (tracerProvider.getIfAvailable() == null) {
            log.warn(PREFIX + "未装配 Tracer，日志中不会出现 traceId。"
                    + "请确认 micrometer-tracing-bridge-otel 与 spring-boot-starter-actuator 均在 classpath 上");
            return;
        }
        if (spanExporterProvider.orderedStream().findAny().isPresent()) {
            log.info(PREFIX + "链路追踪已启用 — 采样率={}，span 将上报到远端",
                    environment.getProperty("management.tracing.sampling.probability", "0.1"));
        } else {
            log.info(PREFIX + "链路追踪已启用 — 未配置 exporter，span 不上报，仅提供日志用的 traceId");
        }
    }
}
