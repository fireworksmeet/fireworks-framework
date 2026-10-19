package com.yzm.fireworks.tracing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 链路追踪默认值注入器：把 Micrometer Tracing 约定的 MDC 键填进日志关联段。
 *
 * <p><b>为什么由本模块负责</b>：{@code fireworks-common} 的统一日志格式只预留关联段占位
 * （{@code ${logging.pattern.correlation:}}），刻意不引用任何 MDC 键——因为键名取决于追踪实现
 * （Micrometer 是 {@code traceId} / {@code spanId}，Agent 探针多为 {@code trace_id} / {@code span_id}）。
 * 既然本模块就是「用 Micrometer Tracing 做追踪」的那一方，键名的归属自然落在这里：谁提供了
 * {@code traceId} / {@code spanId}，谁负责把这两个键名填进格式串，两者必须成对出现。
 *
 * <p><b>为什么不注入采样率</b>：采样率只决定 span 是否被<b>记录并上报</b>，与 traceId 是否生成、
 * 是否写入 MDC 完全无关——OTel 桥在 scope attach 时无条件把 {@code SpanContext#getTraceId()} 写入 MDC。
 * 本模块自身不引入任何 exporter，采样率在这里没有任何可观测效果，注入它只会悄悄改掉业务侧
 * Boot 的默认值（{@code 0.1}），因此不做任何设置。
 *
 * <p><b>为什么用 {@link EnvironmentPostProcessor}</b>：日志格式在 {@code ApplicationContext}
 * 创建之前就已确定，只有环境准备阶段还来得及影响它。这里用 {@code addLast} 把默认值追加到
 * 属性源列表<b>末尾</b>（最低优先级），因此 {@code application.yml}、命令行参数、环境变量中的
 * 任何显式配置都能覆盖它——业务要对接 Agent、换用其他键名，或直接清空关联段，配置即可。
 *
 * <p><b>注册方式</b>：见 {@code META-INF/spring.factories} 中的
 * {@code org.springframework.boot.env.EnvironmentPostProcessor} 键。Spring Boot 3.x 只从
 * {@code spring.factories} 加载该接口（{@code *.imports} 仅用于自动配置类）。
 *
 * @author JYuan
 */
public class TracingDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 属性源名称。默认值排在所有属性源末尾，仅作为兜底，绝不覆盖业务显式配置。
     */
    private static final String PROPERTY_SOURCE_NAME = "fireworks-tracing-defaults";

    /**
     * 关联段内容：Micrometer Tracing 写入 MDC 的键名，与 common 格式串中的占位一一对应。
     *
     * <p>用 Logback 的 {@code :-} 默认值语法：{@code %X{key:-}} 表示该键缺失时渲染为空串。
     * 整串不含 {@code ${}}，因此被 common 的格式串嵌套引用时不会被 Spring 的占位符解析误伤。
     */
    private static final String CORRELATION_PATTERN = "[%X{traceId:-},%X{spanId:-}]";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("logging.pattern.correlation", CORRELATION_PATTERN);
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    /**
     * {@link Ordered#LOWEST_PRECEDENCE}：在所有 {@code EnvironmentPostProcessor} 中最后执行，
     * 以保证 {@code application.yml} 等外部配置已加载完毕，从而让 {@code addLast} 落到真正的最低优先级。
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
