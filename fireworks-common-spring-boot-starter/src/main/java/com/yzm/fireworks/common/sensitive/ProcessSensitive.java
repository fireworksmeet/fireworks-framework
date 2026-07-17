package com.yzm.fireworks.common.sensitive;

import com.yzm.fireworks.common.util.StrUtil;


/**
 * @author JYuan
 * 脱敏处理接口
 */
public interface ProcessSensitive {

    /**
     * 字符串脱敏，入口统一做空值保护
     */
    String process(String str);

    /**
     * 将 [start, end) 区间内的字符替换为 '*'
     *
     * @param str   原始字符串
     * @param start 起始索引（含）
     * @param end   结束索引（不含）
     * @return 脱敏后的字符串；若参数非法或字符串为空则返回原值
     */
    default String hide(String str, int start, int end) {
        return StrUtil.mask(str, start, end);
    }

    /**
     * 将整个字符串替换为等长 '*'（常用于密码场景）
     */
    default String hideAll(String str) {
        return StrUtil.maskAll(str);
    }
}