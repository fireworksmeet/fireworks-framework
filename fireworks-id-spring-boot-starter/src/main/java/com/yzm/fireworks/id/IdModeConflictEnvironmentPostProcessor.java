package com.yzm.fireworks.id;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * ID starter 唯一的启动期逻辑：校验 CosId 的两种模式没有同时开启。
 * <p>
 * {@code cosid.segment.enabled} / {@code cosid.snowflake.enabled} 都是 CosId 自己的属性，本模块
 * <b>不定义任何配置项、也不改写任何属性</b>；只在两者<b>同时为 {@code true}</b> 时拒绝启动——
 * CosId 允许二者并存，但共享生成器（{@code __share__}）只有一个生效，行为不可预期，
 * 属于"配置与预期不一致"的静默故障，因此在启动期直接拦下。
 * <p>
 * 除此之外的一切（机器号分发、适配包、号段表、位分配……）都交由 CosId 自行处理与报错，
 * 本模块不重复校验——避免同一处配置出现两套报错规则。
 * <p>
 * <b>为什么用 {@link EnvironmentPostProcessor}</b>：要在任何 Bean（含 CosId 自己的自动配置）实例化之前
 * 判定并终止启动，唯一的可靠时机是环境准备阶段。
 */
public class IdModeConflictEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String COSID_SEGMENT_ENABLED = "cosid.segment.enabled";
    private static final String COSID_SNOWFLAKE_ENABLED = "cosid.snowflake.enabled";

    /**
     * 最低优先级：必须晚于 ConfigData，读到的才是 application.yaml / 配置中心合并后的最终值。
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Boolean segmentEnabled = getBoolean(environment, COSID_SEGMENT_ENABLED);
        Boolean snowflakeEnabled = getBoolean(environment, COSID_SNOWFLAKE_ENABLED);

        if (Boolean.TRUE.equals(segmentEnabled) && Boolean.TRUE.equals(snowflakeEnabled)) {
            throw new IllegalStateException("""
                    两种 ID 模式互斥，不能同时开启：cosid.segment.enabled 与 cosid.snowflake.enabled 都是 true。
                    CosId 允许二者并存，但共享生成器（__share__）只会有一个生效，行为不可预期，故本 starter 直接拒绝启动。
                    请按 README 的配置模板二选一：
                      · 号段模式：保留 cosid.segment.enabled=true（并移除 cosid.snowflake.enabled）
                      · 雪花模式：保留 cosid.snowflake.enabled=true（并移除 cosid.segment.enabled）""");
        }
    }

    /**
     * 用 {@link Binder} 读取以兼容 Spring Boot 的宽松绑定（如 {@code ENABLED}、{@code enabled} 等写法）。
     */
    private Boolean getBoolean(ConfigurableEnvironment environment, String name) {
        return Binder.get(environment).bind(name, Boolean.class).orElse(null);
    }
}
