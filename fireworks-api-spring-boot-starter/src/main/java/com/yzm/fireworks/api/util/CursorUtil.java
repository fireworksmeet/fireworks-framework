package com.yzm.fireworks.api.util;

import com.yzm.fireworks.common.util.Base64Util;
import org.springframework.util.StringUtils;

import static com.yzm.fireworks.common.constants.StringPool.UNDERSCORE;

/**
 * 游标与 Base64 高性能处理工具类
 *
 * @author JYuan
 */
public class CursorUtil {

    private CursorUtil() {
        // 私有构造，禁止实例化
        throw new AssertionError("Utility class");
    }

    /**
     * 构建复合游标（如：时间戳 + ID）
     *
     * 示例：CursorUtil.build(1769587200000L, 10086L) -> "MTc2OTU4NzIwMDAwMH4xMDA4Ng"
     *
     * @param values 游标字段列表（按 SQL 排序顺序传入）
     * @return 加密后的单字符串游标
     */
    public static String build(Object... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        // 使用 StringUtils 拼接，如 "1769587200000_10086"
        String raw = StringUtils.arrayToDelimitedString(values, UNDERSCORE);
        return Base64Util.encodeUrl(raw);
    }

    /**
     * 解析复合游标为原始字符串数组
     *
     * @param cursor 客户端传回的加密游标
     * @return 解码拆分后的字段数组（查无结果返回空数组，避免 NPE）
     */
    public static String[] parse(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return new String[0];
        }
        try {
            String raw = Base64Util.decode(cursor);
            if (!StringUtils.hasText(raw)) {
                return new String[0];
            }
            return raw.split(UNDERSCORE);
        } catch (IllegalArgumentException e) {
            // 防御性编程：非法/非法篡改的 Base64 字符串，优雅降级为非法游标，避免直接抛 500
            return new String[0];
        }
    }
}