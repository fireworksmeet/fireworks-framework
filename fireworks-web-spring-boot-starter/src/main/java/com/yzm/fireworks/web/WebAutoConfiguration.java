package com.yzm.fireworks.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.web.config.properties.ClientInfoProperties;
import com.yzm.fireworks.web.config.properties.IpLocationProperties;
import com.yzm.fireworks.web.handler.GlobalExceptionHandler;
import com.yzm.fireworks.web.handler.ResultErrorAttributes;
import com.yzm.fireworks.web.handler.UserAgentAnalyzerWarmupHandler;
import com.yzm.fireworks.web.service.IpLocationService;
import com.yzm.fireworks.web.util.ClientInfoUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Web 自动配置
 * <p>
 * 【注册方式】框架类不在业务应用的组件扫描范围内，因此组件一律通过 {@code @Bean} 或
 * {@code @Import} 显式注册。此处**刻意不使用 {@code @ComponentScan}**：官方明确要求
 * 自动配置类不得启用组件扫描（它会破坏 {@code @ConditionalOnMissingBean} 的评估时机，
 * 因为扫描发生在配置类解析阶段，早于其他 bean 定义注册完毕），需要引入组件时应改用
 * {@code @Import}。
 *
 * @author JYuan
 */
@AutoConfiguration(before = ErrorMvcAutoConfiguration.class)
@Import(GlobalExceptionHandler.class)
@EnableConfigurationProperties({IpLocationProperties.class, ClientInfoProperties.class})
public class WebAutoConfiguration {

    /**
     * 统一错误响应装配，替换 Spring Boot 默认的 {@code DefaultErrorAttributes}
     * <p>
     * 【说明】保留官方 {@code /error} 链路（{@code BasicErrorController} + {@code ErrorAttributes}），
     * 仅替换响应体的组织方式，使其与业务接口的 {@link com.yzm.fireworks.api.Result} 结构一致。
     * 不要自定义 {@code ErrorPage}——那会覆盖官方链路，并使 {@code BasicErrorController}
     * 成为永远不会被触发的"僵尸" Bean，还会连带把 HTTP 状态强制重置为 200。
     * <p>
     * 【顺序】{@code before = ErrorMvcAutoConfiguration.class} 用于保证本 Bean 先于 Spring Boot 的
     * {@code DefaultErrorAttributes} 注册，避免容器中出现两个 {@code ErrorAttributes}，
     * 导致 {@code BasicErrorController} 注入歧义。
     */
    @Bean
    @ConditionalOnMissingBean(ErrorAttributes.class)
    public ErrorAttributes errorAttributes(ObjectMapper objectMapper) {
        return new ResultErrorAttributes(objectMapper);
    }

    /**
     * IP地理位置服务，通过 fireworks.web.ip-location.enabled=true 开启
     */
    @ConditionalOnProperty(prefix = "fireworks.web.ip-location", name = "enabled", havingValue = "true")
    @Bean
    public IpLocationService ipLocationService(IpLocationProperties properties) {
        return new IpLocationService(properties);
    }

    /**
     * 客户端信息分析，通过 fireworks.web.client-info.enabled=true 开启。
     * 开启后激活 UA 解析功能并注册预热处理器。
     */
    @ConditionalOnProperty(prefix = "fireworks.web.client-info", name = "enabled", havingValue = "true")
    @Bean
    public UserAgentAnalyzerWarmupHandler userAgentAnalyzerWarmupHandler() {
        ClientInfoUtil.setUaEnabled(true);
        return new UserAgentAnalyzerWarmupHandler();
    }
}
