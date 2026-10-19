package com.yzm.fireworks.webmvc.logging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 访问日志默认值注入器：以「最低优先级配置」让 LogBook 开箱即用。
 *
 * <p><b>为什么需要它</b>：LogBook 有两个必须显式配置、否则静默失效的点——
 *
 * <ol>
 *   <li>它的日志写入器判断的是 {@code logger.isTraceEnabled()}，因此 {@code logger} 必须是 TRACE
 *       级别才会输出。日志框架默认是 INFO，不打开就完全没有任何访问日志，<b>且不报错</b>；
 *       而访问日志属框架承诺的默认能力（见 {@code docs/conventions/observability.md}），
 *       不应要求每个业务自行记住这一行。</li>
 *   <li>{@code write.max-body-size} 的库默认值为 {@code -1}（不限制），任意大的请求/响应 body
 *       都会被完整写入日志并驻留内存。这里给一个兜底上限，避免个别大 body 接口撑爆日志体积。</li>
 * </ol>
 *
 * <p><b>本类不注入策略</b>：{@code logbook.strategy} 决定是否记录 body，直接关系业务排查时
 * 能看到什么，属于业务侧的取舍，框架不替业务决定，保持 LogBook 自身的默认值。生产环境的推荐
 * 配置见模块 {@code README.md}。
 *
 * <p><b>为什么是 addLast（最低优先级）</b>：默认值排在属性源列表<b>末尾</b>，因此
 * {@code application.yml}、命令行参数、环境变量中的任何显式配置都能覆盖它。
 *
 * <p><b>注册方式</b>：见 {@code META-INF/spring.factories} 中的
 * {@code org.springframework.boot.env.EnvironmentPostProcessor} 键。Spring Boot 3.x 只从
 * {@code spring.factories} 加载该接口（{@code *.imports} 仅用于自动配置类）。
 *
 * @author JYuan
 */
public class LogbookDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 属性源名称。默认值排在所有属性源末尾，仅作为兜底，绝不覆盖业务显式配置。
     */
    private static final String PROPERTY_SOURCE_NAME = "fireworks-webmvc-logbook-defaults";

    /**
     * LogBook 的输出 logger。级别必须是 TRACE 才会有输出（库的既定行为，见类注释）。
     */
    private static final String LOGBOOK_LOGGER = "org.zalando.logbook";

    /**
     * 单条日志 body 的兜底上限（字节）。
     */
    private static final int MAX_BODY_SIZE = 8192;

    private static final boolean LOGBOOK_AVAILABLE =
            ClassUtils.isPresent("org.zalando.logbook.Logbook", null);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!LOGBOOK_AVAILABLE) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("logging.level." + LOGBOOK_LOGGER, "TRACE");
        defaults.put("logbook.write.max-body-size", MAX_BODY_SIZE);
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
