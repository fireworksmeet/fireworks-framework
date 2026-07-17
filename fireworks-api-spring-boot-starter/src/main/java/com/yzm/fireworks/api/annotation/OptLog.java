package com.yzm.fireworks.api.annotation;


import com.yzm.fireworks.api.enums.OptLogType;

import java.lang.annotation.*;

/**
 * @author JYuan
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OptLog {

    /**
     * 所属模块，如 "用户管理"、"角色管理"
     */
    String module() default "";

    /**
     * 操作类型
     */
    OptLogType type();

    /**
     * 日志描述，如 "新增系统用户"
     */
    String description();

    /**
     * 是否记录请求参数（JSON 序列化后存储），默认 true
     * 对于包含敏感信息（密码、Token）的接口可设为 false
     */
    boolean recordArgs() default true;

    /**
     * 是否记录返回值（JSON 序列化后存储），默认 false
     * 返回值可能较大，按需开启
     */
    boolean recordResult() default false;
}
