package com.yzm.fireworks.common.constants;

import lombok.experimental.UtilityClass;

/**
 * 字符串常量池
 *
 * @author JYuan
 */
@UtilityClass
public class StringPool {

    public static final String EMPTY = "";
    public static final String COMMA = ",";
    public static final String COLON = ":";
    public static final String SEMICOLON = ";";
    public static final String SINGLE_QUOTATION = "'";
    public static final String QUOTATION = "\"";
    public static final String HYPHEN = "-";
    public static final String DOT = ".";
    public static final String UNDERSCORE = "_";
    public static final String AT = "@";
    public static final String POUND = "#";
    public static final String PERCENT = "%";
    public static final String AMPERSAND = "&";
    public static final String ASTERISK = "*";
    public static final String EQUAL = "=";
    public static final String QUESTION_MARK = "?";
    public static final String EXCLAMATION_MARK = "!";
    public static final String LEFT_BRACKET = "(";
    public static final String RIGHT_BRACKET = ")";
    public static final String LEFT_BRACE = "{";
    public static final String RIGHT_BRACE = "}";
    public static final String LEFT_SQUARE_BRACKET = "[";
    public static final String RIGHT_SQUARE_BRACKET = "]";
    public static final String LEFT_ANGLE_BRACKET = "<";
    public static final String RIGHT_ANGLE_BRACKET = ">";
    public static final String SPACE = " ";
    public static final String VERTICAL_BAR = "|";
    public static final String SLASH = "/";
    public static final String DOUBLE_SLASH = "//";
    public static final String BACKSLASH = "\\";
    public static final String DOUBLE_BACKSLASH = "\\\\";
    public static final String HOST_PREFIX = "://";
    public static final String HTTP_HOST_PREFIX = "http://";
    public static final String HTTPS_HOST_PREFIX = "https://";
    public static final String CODE = "code";
    public static final String LOCALHOST = "localhost";
    public static final String LOCALHOST_IPV4 = "127.0.0.1";
    public static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    /**
     * 日志截断后缀
     */
    public static final String TRUNCATED_SUFFIX = "...[truncated]";

    // ==================== 错误码 / 未知值 ====================

    /**
     * 未知：既用于数据缺失时的占位值（如无法识别的 UA 字段），也用于未归类的失败码。
     * <p>
     * 大小写统一为大写，与真实取值（如 {@code ios} / {@code web}）区分开，一眼可辨「不是有效值」。
     */
    public static final String UNKNOWN = "UNKNOWN";

    /**
     * 网络层失败：连接超时、IO 异常等，通常可重试。
     */
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
}
