package com.yzm.fireworks.id;

import me.ahoo.cosid.provider.IdGeneratorProvider;

/**
 * 启动期的绑定器：把容器里的 {@link IdGeneratorProvider} 交给静态 API {@link IdUtil}。
 * <p>
 * <b>为什么单独一个类</b>：工具类应当是纯静态、私有构造、不可实例化的；但它又需要一次依赖注入
 * 才能拿到 provider。把「取 Bean + 赋值」这唯一的副作用放在本类，{@link IdUtil} 就能保持纯粹。
 * <p>
 * 包内可见：只由 {@link IdAutoConfiguration} 注册，不作为对外 API。
 *
 * @author JYuan
 */
final class IdUtilBinder {

    /**
     * 绑定 provider——赋值即发生在本构造器里，属于 Bean 实例化阶段，不需要生命周期钩子。
     * <p>
     * 绑的是 provider <b>引用</b>：CosId 的生成器注册器（{@code XxxBeanRegistrar}）晚于本 Bean 执行也没关系，
     * 它们注册进 provider 的生成器，{@link IdUtil} 读到的是同一份内容。
     */
    IdUtilBinder(IdGeneratorProvider idGeneratorProvider) {
        IdUtil.bind(idGeneratorProvider);
    }
}
