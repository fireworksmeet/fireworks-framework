package com.yzm.fireworks.web.annotation;


import com.yzm.fireworks.web.aspectj.SystemLogAspect;
import com.yzm.fireworks.web.properties.SystemLogProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author JYuan
 * 开启系统日志: 会记录每个请求的参数和响应时间
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SystemLogAspect.class)
@EnableConfigurationProperties(SystemLogProperties.class)
public @interface EnableSystemLog {

}
