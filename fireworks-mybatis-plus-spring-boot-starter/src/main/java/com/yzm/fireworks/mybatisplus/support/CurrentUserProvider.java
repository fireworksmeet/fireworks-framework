package com.yzm.fireworks.mybatisplus.support;

/**
 * 当前登录人提供者（审计字段自动填充的来源）。
 * <p>
 * 框架不绑定任何鉴权体系，也不提供默认实现：需要审计人时，业务在自己的配置类里声明一个本类型 Bean 即可；
 * 不声明时 {@code createdBy} / {@code updatedBy} 不填充，也不会写占位值。
 * <p>
 * 用了 {@code fireworks-security} 的工程，Bean 体直接返回 {@code SecurityUserContext::getUserId} 即可。
 *
 * @author JYuan
 */
@FunctionalInterface
public interface CurrentUserProvider {

    /**
     * 当前登录用户 ID。
     *
     * @return 用户 ID；<b>无登录上下文（如定时任务、MQ 消费）时返回 {@code null}</b>，
     * 此时审计字段不会被填充，也不会写入错误的占位值
     */
    Long getUserId();
}
