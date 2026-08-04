package com.yzm.fireworks.web.config;

import com.yzm.fireworks.web.config.properties.SystemLogProperties;
import com.yzm.fireworks.web.filter.SystemLogFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

public class SystemLogConfiguration {

    @Bean
    public FilterRegistrationBean<SystemLogFilter> systemLogFilterRegistration(SystemLogProperties properties) {
        FilterRegistrationBean<SystemLogFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SystemLogFilter(properties));
        registration.addUrlPatterns("/*");
        // 设置最高优先级，确保最先包裹 Request，且能拦截到最全面的 HTTP 状态（如 404/500/401 等）
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}