package com.yzm.fireworks.oplog.config;

import lombok.Data;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;

/**
 * 操作日志配置项。
 *
 * <p>前缀：{@code fireworks.oplog}。
 *
 * <p>所有开关与参数都走 {@code fireworks.oplog.*}，不需要 {@code @EnableXxx} 注解：
 * 注解形式的属性无法被 {@code spring-configuration-metadata} 发现，也就没有 IDE 提示与校验。
 *
 * <p>不提供对象差异比对（{@code java-object-diff}）能力：它由十余个模板配置驱动，
 * 与本组件"记录操作日志"的目标无关；因此也不存在相关配置项，避免"有配置无实现"。
 *
 * @author muzhantong
 */
@ConfigurationProperties(prefix = "fireworks.oplog")
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Data
public class LogRecordProperties {

    /**
     * 租户 / 应用标识，写入每条操作日志
     */
    private String tenant = "";

    /**
     * 操作日志 advisor 的优先级
     */
    private int order = Ordered.LOWEST_PRECEDENCE;

    /**
     * 操作日志与业务是否同一事务
     */
    private boolean joinTransaction = false;
}
