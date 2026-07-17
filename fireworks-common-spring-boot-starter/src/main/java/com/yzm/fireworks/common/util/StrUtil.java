package com.yzm.fireworks.common.util;

import org.springframework.util.StringUtils;

import static com.yzm.fireworks.common.constants.StringPool.TRUNCATED_SUFFIX;

public final class StrUtil {

    private StrUtil() {
        throw new AssertionError("Utility class");
    }

    // ==================== 截断 ====================

    public static String truncate(String str, int maxLength) {
        return truncate(str, maxLength, TRUNCATED_SUFFIX);
    }

    public static String truncate(String str, int maxLength, String suffix) {
        if (str == null || maxLength < 0 || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + suffix;
    }

    // ==================== 去除前后缀 ====================

    public static String removePrefix(String str, String prefix) {
        if (!StringUtils.hasText(str) || !StringUtils.hasText(prefix)) {
            return str;
        }
        if (str.startsWith(prefix)) {
            return str.substring(prefix.length());
        }
        return str;
    }

    public static String removeSuffix(String str, String suffix) {
        if (!StringUtils.hasText(str) || !StringUtils.hasText(suffix)) {
            return str;
        }
        if (str.endsWith(suffix)) {
            return str.substring(0, str.length() - suffix.length());
        }
        return str;
    }

    // ==================== 子串提取 ====================

    public static String substringBefore(String str, String separator) {
        if (!StringUtils.hasText(str) || separator == null) {
            return str;
        }
        int index = str.indexOf(separator);
        return index < 0 ? str : str.substring(0, index);
    }

    public static String substringAfter(String str, String separator) {
        if (!StringUtils.hasText(str) || separator == null) {
            return str;
        }
        int index = str.indexOf(separator);
        if (index < 0) {
            return "";
        }
        return str.substring(index + separator.length());
    }

    public static String substringBeforeLast(String str, String separator) {
        if (!StringUtils.hasText(str) || separator == null) {
            return str;
        }
        int index = str.lastIndexOf(separator);
        return index < 0 ? str : str.substring(0, index);
    }

    public static String substringAfterLast(String str, String separator) {
        if (!StringUtils.hasText(str) || separator == null) {
            return str;
        }
        int index = str.lastIndexOf(separator);
        if (index < 0) {
            return "";
        }
        return str.substring(index + separator.length());
    }

    // ==================== 默认值 ====================

    public static String defaultIfBlank(String str, String defaultStr) {
        return StringUtils.hasText(str) ? str : defaultStr;
    }

    public static String defaultIfNull(String str, String defaultStr) {
        return str != null ? str : defaultStr;
    }

    @SafeVarargs
    public static <T extends CharSequence> T firstNonBlank(T... values) {
        if (values != null) {
            for (T value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    // ==================== 掩码 ====================

    public static String mask(String str, int start, int end) {
        return mask(str, start, end, '*');
    }

    public static String mask(String str, int start, int end, char maskChar) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        if (start < 0 || end < 0 || start >= end || end > str.length()) {
            return str;
        }
        char[] chars = str.toCharArray();
        for (int i = start; i < end; i++) {
            chars[i] = maskChar;
        }
        return String.valueOf(chars);
    }

    public static String maskAll(String str) {
        return maskAll(str, '*');
    }

    public static String maskAll(String str, char maskChar) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        return String.valueOf(maskChar).repeat(str.length());
    }

    // ==================== 驼峰 ↔ 连字符 ====================

    public static String camelToHyphen(String str) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('-');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static String hyphenToCamel(String str) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '-') {
                nextUpper = true;
            } else if (nextUpper) {
                result.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    // ==================== 统计 ====================

    public static int countMatches(String str, String sub) {
        if (!StringUtils.hasText(str) || sub == null || sub.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(sub, index)) >= 0) {
            count++;
            index += sub.length();
        }
        return count;
    }
}
