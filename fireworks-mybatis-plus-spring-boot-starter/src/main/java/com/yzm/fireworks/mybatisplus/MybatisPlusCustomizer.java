package com.yzm.fireworks.mybatisplus;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;

/**
 * MyBatis-Plus 拦截器扩展点。
 * <p>
 * 框架只负责装配<b>分页</b>拦截器（这是人人都要、且最容易配错方言的一步）；
 * 其余插件（乐观锁、防全表更新/删除、多租户、动态表名等）<b>不内置</b>，需要时通过本扩展点<b>追加</b>：
 *
 * <pre>{@code
 * @Bean
 * public MybatisPlusCustomizer optimisticLockerCustomizer() {
 *     return interceptor -> interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
 * }
 * }</pre>
 *
 * <p>相比「自己声明 {@code MybatisPlusInterceptor} Bean」，本扩展点不会覆盖掉框架装配的分页拦截器，
 * 因此不需要重复配置分页。
 *
 * @author JYuan
 */
@FunctionalInterface
public interface MybatisPlusCustomizer {

    /**
     * 在框架已装配好分页的 {@link MybatisPlusInterceptor} 上追加自定义 inner interceptor。
     *
     * @param interceptor 框架已初始化的拦截器
     */
    void customize(MybatisPlusInterceptor interceptor);
}
