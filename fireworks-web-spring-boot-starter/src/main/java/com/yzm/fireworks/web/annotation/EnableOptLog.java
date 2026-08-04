package com.yzm.fireworks.web.annotation;


import com.yzm.fireworks.web.config.OptLogConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author JYuan
 * 开启操作日志: 可以用于记录用户的操作信息
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(OptLogConfiguration.class)
public @interface EnableOptLog {

}
