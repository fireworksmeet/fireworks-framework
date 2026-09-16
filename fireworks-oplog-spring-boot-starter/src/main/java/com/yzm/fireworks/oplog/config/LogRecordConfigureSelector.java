package com.yzm.fireworks.oplog.config;

import com.yzm.fireworks.oplog.annotation.EnableLogRecord;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.lang.Nullable;

/**
 * 操作日志配置选择器
 * <p>
 * 由 {@code @EnableLogRecord} 通过 {@code @Import} 引入，父类
 * {@link AdviceModeImportSelector} 负责读取注解上的 {@link EnableLogRecord#mode()} 属性，
 * 并按返回结果将对应配置类注册到 Spring 容器。
 * <p>
 * 选择逻辑：
 * <ul>
 *     <li>{@link AdviceMode#PROXY}（默认）：注册 {@link AutoProxyRegistrar} 以启用
 *     AOP 自动代理，同时注册日志自动配置类</li>
 *     <li>{@link AdviceMode#ASPECTJ}：仅注册日志自动配置类，代理由外部
 *     AspectJ 织入机制负责</li>
 * </ul>
 *
 * @author mzt.
 */
public class LogRecordConfigureSelector extends AdviceModeImportSelector<EnableLogRecord> {

    /**
     * 根据代理模式返回需要注册的配置类全限定名
     *
     * @param adviceMode 来自 {@code @EnableLogRecord#mode()} 的代理模式
     * @return 待注册的配置类全限定名数组
     */
    @Override
    @Nullable
    public String[] selectImports(AdviceMode adviceMode) {
        return switch (adviceMode) {
            case PROXY ->
                    new String[]{AutoProxyRegistrar.class.getName(), LogRecordProxyAutoConfiguration.class.getName()};
            case ASPECTJ -> new String[]{LogRecordProxyAutoConfiguration.class.getName()};
        };
    }
}
