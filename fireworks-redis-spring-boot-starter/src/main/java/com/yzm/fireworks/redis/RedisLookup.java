package com.yzm.fireworks.redis;

import lombok.experimental.UtilityClass;

import java.util.Map;

/**
 * 按名称查数据源资源，统一“未找到”的错误文案。
 *
 * <p>{@link RedisUtil#on(String)} 与 {@code LockService#on(String)} 需要同一套提示
 * （含可用列表），否则业务排查时会看到两种措辞。
 *
 * <p><b>内部工具</b>：公开是因为 {@code lock} 子包需要访问，不是对外 API，不承诺兼容性。
 *
 * @author JYuan
 */
@UtilityClass
public class RedisLookup {

    /**
     * @param map  数据源名称 → 资源
     * @param name 数据源名称
     * @param what 资源类型，用于错误提示（如 {@code "Redis datasource"}）
     * @throws IllegalArgumentException 名称不存在
     */
    public static <T> T require(Map<String, T> map, String name, String what) {
        T value = map.get(name);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Unknown " + what + " '" + name + "'. Available: " + map.keySet());
        }
        return value;
    }
}
