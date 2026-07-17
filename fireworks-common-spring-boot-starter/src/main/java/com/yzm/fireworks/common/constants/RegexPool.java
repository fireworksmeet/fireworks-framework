package com.yzm.fireworks.common.constants;

/**
 * 正则表达式常量池
 * <p>
 * 企业常用正则表达式集合，可用于：
 * 1. Bean Validation 注解：@Pattern(regexp = RegexPool.REGEX_MOBILE, message = RegexPool.REGEX_MOBILE_MSG)
 * 2. 编程式校验：ValidationUtil.isMobile(str)
 * </p>
 *
 * @author JYuan
 */
public final class RegexPool {

    private RegexPool() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // ==================== 账号/密码 ====================

    /** 用户名：2-16位，支持中文、字母、数字、下划线 */
    public static final String REGEX_USERNAME = "^[\\u4e00-\\u9fa5_a-zA-Z0-9]{2,16}$";
    public static final String REGEX_USERNAME_MSG = "仅支持2-16位中、英文、数字、_";

    /** 密码（普通）：6-16位，必须同时包含字母和数字 */
    public static final String REGEX_PASSWORD = "^(?=.*[a-zA-Z])(?=.*\\d)[a-zA-Z0-9]{6,16}$";
    public static final String REGEX_PASSWORD_MSG = "仅支持6-16位，必须同时包含字母和数字";

    /** 密码（强）：8-20位，必须包含大小写字母、数字及特殊字符 */
    public static final String REGEX_PWD_STRONG = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?])[\\S]{8,20}$";
    public static final String REGEX_PWD_STRONG_MSG = "8-20位，须含大小写字母、数字及特殊字符";

    // ==================== 联系方式 ====================

    /** 手机号：1[3-9] 开头的11位 */
    public static final String REGEX_MOBILE = "^1[3-9]\\d{9}$";
    public static final String REGEX_MOBILE_MSG = "请输入正确的手机号";

    /** 邮箱 */
    public static final String REGEX_EMAIL = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";
    public static final String REGEX_EMAIL_MSG = "请输入正确的邮箱地址";

    /** 手机号或邮箱 */
    public static final String REGEX_EMAIL_OR_MOBILE = "^([a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,})|(1[3-9]\\d{9})$";
    public static final String REGEX_EMAIL_OR_MOBILE_MSG = "请输入正确的邮箱或手机号";

    /** 固定电话（含区号、分机号） */
    public static final String REGEX_PHONE = "^(0\\d{2,3}-?)?[2-9]\\d{6,7}(-\\d{1,4})?$";
    public static final String REGEX_PHONE_MSG = "请输入正确的固定电话";

    // ==================== 证件 ====================

    /** 18位身份证（基础格式校验） */
    public static final String REGEX_ID_CARD = "^\\d{17}[0-9Xx]$";
    public static final String REGEX_ID_CARD_MSG = "请输入正确的身份证号码";

    /** 统一社会信用代码（18位） */
    public static final String REGEX_CREDIT_CODE = "^[0-9A-HJ-NP-RT-UW-Y]{2}\\d{6}[0-9A-HJ-NP-RT-UW-Y]{10}$";
    public static final String REGEX_CREDIT_CODE_MSG = "请输入正确的统一社会信用代码";

    /** 银行卡号（13-19位） */
    public static final String REGEX_BANK_CARD = "^[1-9]\\d{12,18}$";
    public static final String REGEX_BANK_CARD_MSG = "请输入正确的银行卡号";

    // ==================== 系统/网络 ====================

    /** URL（支持 http/https，端口，路径等） */
    public static final String REGEX_URL = "^https?://[\\w\\-]+(\\.[\\w\\-]+)*(:\\d+)?(/[\\w\\-./?%&=+#@!]*)?$";
    public static final String REGEX_URL_MSG = "请输入正确的URL";

    /** IPv4 地址 */
    public static final String REGEX_IPV4 = "^(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$";
    public static final String REGEX_IPV4_MSG = "请输入正确的IPv4地址";

    /** IPv6 地址 */
    public static final String REGEX_IPV6 = "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$";
    public static final String REGEX_IPV6_MSG = "请输入正确的IPv6地址";

    /** MAC 地址 */
    public static final String REGEX_MAC = "^([0-9A-Fa-f]{2}[:\\-]){5}[0-9A-Fa-f]{2}$";
    public static final String REGEX_MAC_MSG = "请输入正确的MAC地址";

    /** 版本号 x.y.z */
    public static final String REGEX_VERSION = "^\\d+\\.\\d+\\.\\d+$";
    public static final String REGEX_VERSION_MSG = "请输入正确的版本号(x.y.z)";

    // ==================== 生活常用 ====================

    /** 邮政编码 */
    public static final String REGEX_ZIP_CODE = "^[1-9]\\d{5}$";
    public static final String REGEX_ZIP_CODE_MSG = "请输入正确的邮政编码";

    /** 车牌号（含新能源） */
    public static final String REGEX_CAR_NUMBER = "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁夏][A-HJ-NP-Z][A-HJ-NP-Z0-9]{4,5}[A-HJ-NP-Z0-9挂学警港澳]$";
    public static final String REGEX_CAR_NUMBER_MSG = "请输入正确的车牌号";

    /** 中文姓名（2-15位，支持·） */
    public static final String REGEX_REAL_NAME = "^[\\u4e00-\\u9fa5·]{2,15}$";
    public static final String REGEX_REAL_NAME_MSG = "请输入正确的中文姓名";

    /** 微信号：6-20位，字母开头 */
    public static final String REGEX_WECHAT = "^[a-zA-Z][a-zA-Z0-9_\\-]{5,19}$";
    public static final String REGEX_WECHAT_MSG = "请输入正确的微信号";

    // ==================== 日期时间 ====================

    /** 日期 yyyy-MM-dd */
    public static final String REGEX_DATE = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$";
    public static final String REGEX_DATE_MSG = "请输入正确的日期格式(yyyy-MM-dd)";

    /** 时间 HH:mm:ss */
    public static final String REGEX_TIME = "^([01]\\d|2[0-3]):([0-5]\\d):([0-5]\\d)$";
    public static final String REGEX_TIME_MSG = "请输入正确的时间格式(HH:mm:ss)";

    // ==================== 数字/金额/字符 ====================

    /** 正整数（不含0） */
    public static final String REGEX_POS_INT = "^[1-9]\\d*$";
    public static final String REGEX_POS_INT_MSG = "请输入正整数";

    /** 非负整数（含0） */
    public static final String REGEX_NON_NEG_INT = "^(0|[1-9]\\d*)$";
    public static final String REGEX_NON_NEG_INT_MSG = "请输入非负整数";

    /** 金额（最多2位小数） */
    public static final String REGEX_AMOUNT = "^(0|[1-9]\\d{0,14})(\\.\\d{1,2})?$";
    public static final String REGEX_AMOUNT_MSG = "请输入正确的金额（最多2位小数）";

    /** 纯中文 */
    public static final String REGEX_CHINESE = "^[\\u4e00-\\u9fa5]+$";
    public static final String REGEX_CHINESE_MSG = "请输入中文字符";

    /** 纯英文 */
    public static final String REGEX_LETTER = "^[a-zA-Z]+$";
    public static final String REGEX_LETTER_MSG = "请输入英文字母";

    // ==================== 安全校验 ====================

    /** 5位连续递增或递减数字 */
    public static final String REGEX_PWD_CONTINUOUS = "(?:(?:0(?=1)|1(?=2)|2(?=3)|3(?=4)|4(?=5)|5(?=6)|6(?=7)|7(?=8)|8(?=9)){4}|(?:9(?=8)|8(?=7)|7(?=6)|6(?=5)|5(?=4)|4(?=3)|3(?=2)|2(?=1)|1(?=0)){4})\\d";

    /** 3位及以上重复字符 */
    public static final String REGEX_PWD_REPEAT = "(.)\\1{2,}";

    // ==================== 认证相关 ====================

    /** JWT 格式校验 */
    public static final String REGEX_JWT = "^[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]*$";
    public static final String REGEX_JWT_MSG = "Token格式不正确";

}
