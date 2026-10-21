package com.yzm.fireworks.common.util;

import lombok.experimental.UtilityClass;

/**
 * 异常链工具类。
 *
 * @author JYuan
 */
@UtilityClass
public class ExceptionUtil {

    /**
     * 判断 cause 链上是否存在指定类型的异常。
     * <p>
     * 用于识别被第三方 SDK 包装过的异常类型（例如 SDK 把 {@code IOException} 包进自定义异常），
     * 只比较最外层类型会漏判。
     *
     * @param throwable 原始异常，可为 null
     * @param type      目标类型
     * @return 链路中存在该类型返回 true
     */
    public static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }

    /**
     * 取 cause 链最底层的异常；无 cause 时返回自身。
     *
     * @param throwable 原始异常，可为 null
     * @return 根因，入参为 null 时返回 null
     */
    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
