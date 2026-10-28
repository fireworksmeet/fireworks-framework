package com.yzm.fireworks.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 组合条件：仅当 {@code fireworks.redis.multi.enabled} 存在且不为 {@code false} 时装配。
 *
 * <p>替代三处重复的 {@code @ConditionalOnProperty} 字符串——拼错属性名的代价是“多数据源静默不生效”，
 * 收敛到一处后只需保证这里正确。
 *
 * @author JYuan
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "fireworks.redis.multi", name = "enabled")
@interface ConditionalOnMultiRedis {
}
