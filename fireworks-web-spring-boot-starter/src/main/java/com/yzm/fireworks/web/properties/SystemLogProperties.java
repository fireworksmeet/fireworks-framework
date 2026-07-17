package com.yzm.fireworks.web.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 系统请求日志配置项
 *
 * <p>配置前缀：{@code fireworks.web.system-log}
 *
 * <p>示例：
 * <pre>
 * fireworks:
 *   web:
 *     system-log:
 *       print-params: true
 *       print-result: true
 *       slow-threshold-ms: 2000
 *       max-length: 2048
 *       include-headers:
 *         - Authorization
 *         - X-User-Id
 *       exclude-paths:
 *         - /actuator/**
 *         - /health
 * </pre>
 *
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.web.system-log")
@Data
public class SystemLogProperties {

    /**
     * 是否打印请求参数，默认 true
     */
    private boolean printParams = true;

    /**
     * 是否打印返回值，默认 true
     */
    private boolean printResult = true;

    /**
     * 慢请求阈值（毫秒）。
     * 超过该值时将日志级别升级为 WARN，便于告警；0 表示不启用慢请求检测
     */
    private long slowThresholdMs = 0;

    /**
     * 日志内容最大长度（字符数），超出部分截断并追加 "...[truncated]"。
     * -1 表示不限制；默认 2048
     */
    private int maxLength = 2048;

    /**
     * 需要打印到日志的请求 Header 白名单。
     * 只有列表中的 Header 才会出现在日志里，避免敏感 Header 泄露
     */
    private List<String> includeHeaders;

    /**
     * 排除的路径，支持 Ant 风格匹配（如 {@code /actuator/**}）。
     * 匹配到的路径不会记录日志
     */
    private List<String> excludePaths;
}
