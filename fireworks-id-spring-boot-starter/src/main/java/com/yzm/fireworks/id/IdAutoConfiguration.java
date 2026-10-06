package com.yzm.fireworks.id;

import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 把 CosId 的 {@link IdGeneratorProvider} 交给静态 API {@link IdUtil}。
 * <p>
 * {@link IdUtil} 是静态工具类，拿不到依赖注入，所以需要一个 Bean 从容器里取到 provider 再交过去——
 * 这就是本类存在的唯一理由（装配归自动配置，绑定细节不污染工具类）。
 * <p>
 * CosId 自身的配置（{@code cosid.*}）全部由 CosId 的自动配置负责，本类不参与。
 */
@AutoConfiguration
public class IdAutoConfiguration {

    /**
     * 创建 {@link IdUtil} 实例，赋值就发生在其构造器里——即 Bean 实例化阶段，不需要任何生命周期钩子。
     * <p>
     * 绑的是 provider <b>引用</b>：CosId 的生成器注册器（{@code XxxBeanRegistrar}）晚于本 Bean 执行也没关系，
     * 它们往 provider 里注册的生成器，{@link IdUtil} 这边读到的是同一份内容——因此不必等到「所有单例就绪」。
     * <p>
     * {@link IdGeneratorProvider} 由 CosId 的 {@code CosIdAutoConfiguration} <b>无条件</b>提供
     * （与号段 / 雪花开关是否开启无关），因此可以直接构造注入——即使两种模式都关着，应用也能正常启动，
     * 只在真正取号时才 fail-fast。
     * <p>
     * 刻意<b>不加</b> {@code @ConditionalOnMissingBean}：条件一旦把本 Bean 顶掉，静态绑定就会静默失效，
     * 而调用方要到取号时才发现；本 Bean 也没有需要被替换的场景（{@link IdUtil} 的方法全是静态的）。
     */
    @Bean
    public IdUtil idUtil(IdGeneratorProvider idGeneratorProvider) {
        return new IdUtil(idGeneratorProvider);
    }
}
