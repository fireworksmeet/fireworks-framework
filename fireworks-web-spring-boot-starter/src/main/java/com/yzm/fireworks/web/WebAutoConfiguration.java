package com.yzm.fireworks.web;

import com.yzm.fireworks.web.handler.GlobalExceptionHandler;
import com.yzm.fireworks.web.handler.UserAgentAnalyzerWarmupHandler;
import com.yzm.fireworks.web.properties.ClientInfoProperties;
import com.yzm.fireworks.web.properties.IpLocationProperties;
import com.yzm.fireworks.web.service.IpLocationService;
import com.yzm.fireworks.web.util.ClientInfoUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author JYuan
 */
@AutoConfiguration
@EnableConfigurationProperties({IpLocationProperties.class, ClientInfoProperties.class})
public class WebAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 自定义错误页面，tomcat的异常会到该页面执行
     */
    @ConditionalOnMissingBean(ErrorPageRegistrar.class)
    @Bean
    public ErrorPageRegistrar errorPageRegistrar() {
        return registry -> registry.addErrorPages(new ErrorPage("/tomcat-exception"));
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
