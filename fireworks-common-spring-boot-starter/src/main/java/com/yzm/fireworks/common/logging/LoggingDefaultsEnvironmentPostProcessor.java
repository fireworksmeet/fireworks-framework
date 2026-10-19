package com.yzm.fireworks.common.logging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日志默认值注入器：以「最低优先级配置」统一各业务的日志格式，同时不牺牲任何标准开关。
 *
 * <p><b>为什么不放 {@code logback-spring.xml}</b>：本模块是对外发布的 starter，会被多个相互独立的业务应用同时依赖。
 * Spring Boot 在 classpath 上只加载「唯一一份」{@code logback-spring.xml}（先到先得，取决于依赖顺序），
 * 把它放进共享 starter 等于和业务抢这份文件，结果不可控；而且业务必须主动 {@code <include>} 才生效，
 * 忘记写就会静默退回 Boot 默认，反而破坏"统一"这一目标。
 *
 * <p><b>为什么用 {@link EnvironmentPostProcessor}</b>：日志在 {@code ApplicationContext} 创建之前就已初始化，
 * 只有环境准备阶段（{@code EnvironmentPostProcessor} 先于 {@code LoggingApplicationListener} 执行）还来得及影响它。
 * 这里用 {@code addLast} 把默认值追加到属性源列表<b>末尾</b>（最低优先级），因此业务在 {@code application.yml}、
 * 命令行参数、环境变量中的任何一项配置都能覆盖它——{@code logging.pattern.console}、
 * {@code logging.pattern.file} 等标准开关全部保持有效。
 *
 * <p><b>只管"格式"，不管"落盘"</b>：本类不注入 {@code logging.file.*}，因此默认不写日志文件（与 Spring Boot
 * 默认行为一致）。容器化部署应由平台采集 stdout / stderr；需要落盘的业务自行设置 {@code logging.file.name}
 * 或 {@code logging.file.path}，并用 {@code logging.logback.rollingpolicy.*} 控制滚动与保留。
 *
 * <p><b>只预留"关联段的位置"，不注入内容</b>：控制台 / 文件格式里预留了关联段占位
 * （{@code ${logging.pattern.correlation:}}，缺省渲染为空），但本模块<b>既不设置
 * {@code logging.pattern.correlation}，也不引用任何 MDC 键</b>——键名取决于追踪实现：Micrometer Tracing 用
 * {@code traceId} / {@code spanId}，Java Agent 探针（OTel 等）多为 {@code trace_id} / {@code span_id}。
 * 在这里写死某一套，另一套实现下的工程就会把关联段稳定渲染成空值且不报错。
 *
 * <p>内容由追踪实现方注入：{@code fireworks-tracing} 随 {@code fireworks-webmvc} 默认引入，由它填 Micrometer 的键名；
 * 用 Agent 的工程则由业务按实际键名声明 {@code logging.pattern.correlation}。
 *
 * <p><b>仅对 Logback 生效</b>：默认格式使用 Logback 语法（{@code %clr} 等），因此仅当 classpath 上存在 Logback
 * 时才注入；换成 Log4j2 等其他实现的业务不会被写入不兼容的格式。
 *
 * <p><b>注册方式</b>：见 {@code META-INF/spring.factories} 中的
 * {@code org.springframework.boot.env.EnvironmentPostProcessor} 键。注意 Spring Boot 3.x 只从
 * {@code spring.factories} 加载该接口（{@code *.imports} 仅用于自动配置类）。
 *
 * @author JYuan
 */
public class LoggingDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 属性源名称。默认值排在所有属性源末尾，仅作为兜底，绝不覆盖业务显式配置。
     */
    private static final String PROPERTY_SOURCE_NAME = "fireworks-logging-defaults";

    /**
     * 控制台日志格式：在 Boot 默认格式的基础上预留关联段占位（内容由追踪实现注入，见类注释）。
     * 除关联段外，级别、日期、异常转换词仍分别由 {@code logging.pattern.level}、
     * {@code logging.pattern.dateformat}、{@code logging.exception-conversion-word} 控制。
     *
     * <p><b>占位符必须用 Spring 语法（{@code ${key:default}}），不能用 Logback 语法（{@code ${key:-default}}）。</b>
     * 因为该值先作为 Spring 属性被 {@code Environment#getProperty} 解析（Spring 的默认值分隔符是 {@code :}），
     * 之后才交给 Logback。若写成 {@code ${key:-default}}，Spring 会把默认值解析成 {@code -default}，
     * 于是每处取默认值的地方都会多出一个 {@code -}（例如行首多 {@code -}、级别前多 {@code -}）。
     */
    private static final String CONSOLE_LOG_PATTERN = "%clr(%d{${logging.pattern.dateformat:yyyy-MM-dd'T'HH:mm:ss.SSSXXX}}){faint}"
            + " %clr(${logging.pattern.level:%5p}) %clr(${PID:}){magenta} %clr(---){faint}"
            + " %clr(${logging.pattern.correlation:}){yellow}"
            + " %clr([%t]){faint} %clr(%logger{150}){cyan} %clr(:){faint}"
            + " %m%n${logging.exception-conversion-word:%wEx}";

    /**
     * 文件日志格式：与控制台一致，仅去掉颜色渲染。仅当业务开启了文件输出时才会被消费。
     * 占位符同样必须使用 Spring 语法，原因见 {@link #CONSOLE_LOG_PATTERN}。
     */
    private static final String FILE_LOG_PATTERN = "%d{${logging.pattern.dateformat:yyyy-MM-dd'T'HH:mm:ss.SSSXXX}}"
            + " ${logging.pattern.level:%5p} ${PID:} ---"
            + " ${logging.pattern.correlation:}"
            + " [%t] %logger{150} : %m%n${logging.exception-conversion-word:%wEx}";

    private static final boolean LOGBACK_AVAILABLE = ClassUtils.isPresent("ch.qos.logback.classic.Logger", null);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!LOGBACK_AVAILABLE) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("logging.pattern.console", CONSOLE_LOG_PATTERN);
        defaults.put("logging.pattern.file", FILE_LOG_PATTERN);
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    /**
     * {@link Ordered#LOWEST_PRECEDENCE}：在所有 {@code EnvironmentPostProcessor} 中最后执行，
     * 以保证 {@code application.yml} 等外部配置已加载完毕，从而让 {@code addLast} 落到真正的最低优先级。
     * （{@code EnvironmentPostProcessor} 整体仍先于 {@code LoggingApplicationListener} 执行，不影响日志初始化时机。）
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
