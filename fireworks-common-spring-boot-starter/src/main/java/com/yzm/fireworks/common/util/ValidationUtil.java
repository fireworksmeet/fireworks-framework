package com.yzm.fireworks.common.util;

import com.yzm.fireworks.common.constants.RegexPool;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 格式校验工具类
 * <p>
 * 使用方式：
 * ValidationUtils.isMobile("13800138000")          // 返回 boolean
 * ValidationUtils.validateMobile("13800138000")     // 返回 ValidationResult（含错误信息）
 * </p>
 *
 * @author JYuan
 */
public final class ValidationUtil {

    private ValidationUtil() {
    }

    // ==================== 正则常量（从 RegexPool 引用并编译） ====================

    /**
     * 用户名
     */
    private static final Pattern P_USERNAME = Pattern.compile(RegexPool.REGEX_USERNAME);
    /**
     * 密码（普通）
     */
    private static final Pattern P_PASSWORD = Pattern.compile(RegexPool.REGEX_PASSWORD);
    /**
     * 密码（强）
     */
    private static final Pattern P_PWD_STRONG = Pattern.compile(RegexPool.REGEX_PWD_STRONG);
    /**
     * 邮箱
     */
    private static final Pattern P_EMAIL = Pattern.compile(RegexPool.REGEX_EMAIL);
    /**
     * 手机号
     */
    private static final Pattern P_MOBILE = Pattern.compile(RegexPool.REGEX_MOBILE);
    /**
     * 手机号或邮箱
     */
    private static final Pattern P_EMAIL_OR_MOBILE = Pattern.compile(RegexPool.REGEX_EMAIL_OR_MOBILE);
    /**
     * 固定电话
     */
    private static final Pattern P_PHONE = Pattern.compile(RegexPool.REGEX_PHONE);
    /**
     * 18位身份证建议格式
     */
    private static final Pattern P_ID_CARD = Pattern.compile(RegexPool.REGEX_ID_CARD);
    /**
     * 邮政编码
     */
    private static final Pattern P_ZIP_CODE = Pattern.compile(RegexPool.REGEX_ZIP_CODE);
    /**
     * URL
     */
    private static final Pattern P_URL = Pattern.compile(RegexPool.REGEX_URL);
    /**
     * IPv4
     */
    private static final Pattern P_IPV4 = Pattern.compile(RegexPool.REGEX_IPV4);
    /**
     * IPv6
     */
    private static final Pattern P_IPV6 = Pattern.compile(RegexPool.REGEX_IPV6);
    /**
     * MAC 地址
     */
    private static final Pattern P_MAC = Pattern.compile(RegexPool.REGEX_MAC);
    /**
     * 统一社会信用代码
     */
    private static final Pattern P_CREDIT_CODE = Pattern.compile(RegexPool.REGEX_CREDIT_CODE);
    /**
     * 银行卡号
     */
    private static final Pattern P_BANK_CARD = Pattern.compile(RegexPool.REGEX_BANK_CARD);
    /**
     * 车牌号
     */
    private static final Pattern P_CAR_NUMBER = Pattern.compile(RegexPool.REGEX_CAR_NUMBER);
    /**
     * 中文姓名
     */
    private static final Pattern P_REAL_NAME = Pattern.compile(RegexPool.REGEX_REAL_NAME);
    /**
     * 日期 yyyy-MM-dd
     */
    private static final Pattern P_DATE = Pattern.compile(RegexPool.REGEX_DATE);
    /**
     * 时间 HH:mm:ss
     */
    private static final Pattern P_TIME = Pattern.compile(RegexPool.REGEX_TIME);
    /**
     * 正整数
     */
    private static final Pattern P_POS_INT = Pattern.compile(RegexPool.REGEX_POS_INT);
    /**
     * 非负整数
     */
    private static final Pattern P_NON_NEG_INT = Pattern.compile(RegexPool.REGEX_NON_NEG_INT);
    /**
     * 金额
     */
    private static final Pattern P_AMOUNT = Pattern.compile(RegexPool.REGEX_AMOUNT);
    /**
     * 纯中文
     */
    private static final Pattern P_CHINESE = Pattern.compile(RegexPool.REGEX_CHINESE);
    /**
     * 纯英文
     */
    private static final Pattern P_LETTER = Pattern.compile(RegexPool.REGEX_LETTER);
    /**
     * 微信号
     */
    private static final Pattern P_WECHAT = Pattern.compile(RegexPool.REGEX_WECHAT);
    /**
     * 版本号
     */
    private static final Pattern P_VERSION = Pattern.compile(RegexPool.REGEX_VERSION);
    /**
     * JWT
     */
    private static final Pattern P_JWT = Pattern.compile(RegexPool.REGEX_JWT);


    // ==================== 密码安全规则（用于弱密码检测） ====================

    /**
     * 5位连续递增/递减数字
     */
    private static final Pattern P_PWD_CONTINUOUS = Pattern.compile(RegexPool.REGEX_PWD_CONTINUOUS);
    /**
     * 3位及以上重复字符
     */
    private static final Pattern P_PWD_REPEAT = Pattern.compile(RegexPool.REGEX_PWD_REPEAT);


    // ==================== 身份证校验位常量 ====================

    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    // ==================== 校验结果 ====================

    /**
     * 校验结果封装
     */
    @Getter
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }

        @Override
        public String toString() {
            return valid ? "OK" : "FAIL: " + message;
        }
    }

    // ==================== 账号 ====================

    public static boolean isUsername(String s) {
        return matches(P_USERNAME, s);
    }

    public static ValidationResult validateUsername(String s) {
        return check(isUsername(s), RegexPool.REGEX_USERNAME_MSG);
    }

    public static boolean isPassword(String s) {
        return matches(P_PASSWORD, s);
    }

    public static ValidationResult validatePassword(String s) {
        return check(isPassword(s), RegexPool.REGEX_PASSWORD_MSG);
    }

    public static boolean isStrongPassword(String s) {
        return matches(P_PWD_STRONG, s);
    }

    public static ValidationResult validateStrongPassword(String s) {
        return check(isStrongPassword(s), RegexPool.REGEX_PWD_STRONG_MSG);
    }

    // ==================== 联系方式 ====================

    public static boolean isMobile(String s) {
        return matches(P_MOBILE, s);
    }

    public static ValidationResult validateMobile(String s) {
        return check(isMobile(s), RegexPool.REGEX_MOBILE_MSG);
    }

    public static boolean isPhone(String s) {
        return matches(P_PHONE, s);
    }

    public static ValidationResult validatePhone(String s) {
        return check(isPhone(s), RegexPool.REGEX_PHONE_MSG);
    }

    public static boolean isEmail(String s) {
        return matches(P_EMAIL, s);
    }

    public static ValidationResult validateEmail(String s) {
        return check(isEmail(s), RegexPool.REGEX_EMAIL_MSG);
    }

    public static boolean isEmailOrMobile(String s) {
        return matches(P_EMAIL_OR_MOBILE, s);
    }

    public static ValidationResult validateEmailOrMobile(String s) {
        return check(isEmailOrMobile(s), RegexPool.REGEX_EMAIL_OR_MOBILE_MSG);
    }


    // ==================== 证件 ====================

    /**
     * 身份证校验（格式 + 校验位）
     */
    public static boolean isIdCard(String s) {
        if (!matches(P_ID_CARD, s)) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            int digit = s.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                return false;
            }
            sum += digit * ID_WEIGHTS[i];
        }
        return Character.toUpperCase(s.charAt(17)) == ID_CHECK_CODES[sum % 11];
    }

    public static ValidationResult validateIdCard(String s) {
        return check(isIdCard(s), RegexPool.REGEX_ID_CARD_MSG);
    }

    public static boolean isCreditCode(String s) {
        return matches(P_CREDIT_CODE, s);
    }

    public static ValidationResult validateCreditCode(String s) {
        return check(isCreditCode(s), RegexPool.REGEX_CREDIT_CODE_MSG);
    }

    public static boolean isBankCard(String s) {
        return matches(P_BANK_CARD, s);
    }

    public static ValidationResult validateBankCard(String s) {
        return check(isBankCard(s), RegexPool.REGEX_BANK_CARD_MSG);
    }

    // ==================== 地址 / 网络 ====================

    public static boolean isUrl(String s) {
        return matches(P_URL, s);
    }

    public static ValidationResult validateUrl(String s) {
        return check(isUrl(s), RegexPool.REGEX_URL_MSG);
    }

    public static boolean isIpv4(String s) {
        return matches(P_IPV4, s);
    }

    public static ValidationResult validateIpv4(String s) {
        return check(isIpv4(s), RegexPool.REGEX_IPV4_MSG);
    }

    public static boolean isIpv6(String s) {
        return matches(P_IPV6, s);
    }

    public static ValidationResult validateIpv6(String s) {
        return check(isIpv6(s), RegexPool.REGEX_IPV6_MSG);
    }

    public static boolean isMac(String s) {
        return matches(P_MAC, s);
    }

    public static ValidationResult validateMac(String s) {
        return check(isMac(s), RegexPool.REGEX_MAC_MSG);
    }

    public static boolean isZipCode(String s) {
        return matches(P_ZIP_CODE, s);
    }

    public static ValidationResult validateZipCode(String s) {
        return check(isZipCode(s), RegexPool.REGEX_ZIP_CODE_MSG);
    }

    public static boolean isCarNumber(String s) {
        return matches(P_CAR_NUMBER, s);
    }

    public static ValidationResult validateCarNumber(String s) {
        return check(isCarNumber(s), RegexPool.REGEX_CAR_NUMBER_MSG);
    }


    // ==================== 个人信息 ====================

    public static boolean isRealName(String s) {
        return matches(P_REAL_NAME, s);
    }

    public static ValidationResult validateRealName(String s) {
        return check(isRealName(s), RegexPool.REGEX_REAL_NAME_MSG);
    }

    public static boolean isWechat(String s) {
        return matches(P_WECHAT, s);
    }

    public static ValidationResult validateWechat(String s) {
        return check(isWechat(s), RegexPool.REGEX_WECHAT_MSG);
    }


    // ==================== 数字 / 金额 ====================

    public static boolean isPositiveInt(String s) {
        return matches(P_POS_INT, s);
    }

    public static ValidationResult validatePositiveInt(String s) {
        return check(isPositiveInt(s), RegexPool.REGEX_POS_INT_MSG);
    }

    public static boolean isNonNegativeInt(String s) {
        return matches(P_NON_NEG_INT, s);
    }

    public static ValidationResult validateNonNegativeInt(String s) {
        return check(isNonNegativeInt(s), RegexPool.REGEX_NON_NEG_INT_MSG);
    }

    public static boolean isAmount(String s) {
        return matches(P_AMOUNT, s);
    }

    public static ValidationResult validateAmount(String s) {
        return check(isAmount(s), RegexPool.REGEX_AMOUNT_MSG);
    }


    // ==================== 字符类型 ====================

    public static boolean isChinese(String s) {
        return matches(P_CHINESE, s);
    }

    public static ValidationResult validateChinese(String s) {
        return check(isChinese(s), RegexPool.REGEX_CHINESE_MSG);
    }

    public static boolean isLetter(String s) {
        return matches(P_LETTER, s);
    }

    public static ValidationResult validateLetter(String s) {
        return check(isLetter(s), RegexPool.REGEX_LETTER_MSG);
    }


    // ==================== 日期 / 时间 ====================

    /**
     * 日期格式校验（格式 + 合法性）
     * 正则只能拦截 2023-02-31 这类，需配合 LocalDate 二次校验
     */
    public static boolean isDate(String s) {
        if (!matches(P_DATE, s)) {
            return false;
        }
        try {
            LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static ValidationResult validateDate(String s) {
        return check(isDate(s), RegexPool.REGEX_DATE_MSG);
    }

    public static boolean isTime(String s) {
        return matches(P_TIME, s);
    }

    public static ValidationResult validateTime(String s) {
        return check(isTime(s), RegexPool.REGEX_TIME_MSG);
    }

    // ==================== 其他 ====================

    public static boolean isVersion(String s) {
        return matches(P_VERSION, s);
    }

    public static ValidationResult validateVersion(String s) {
        return check(isVersion(s), RegexPool.REGEX_VERSION_MSG);
    }

    public static boolean isJwt(String s) {
        return matches(P_JWT, s);
    }

    public static ValidationResult validateJwt(String s) {
        return check(isJwt(s), RegexPool.REGEX_JWT_MSG);
    }


    // ==================== 密码强度辅助（返回弱密码原因） ====================

    /**
     * 检测密码是否为弱密码
     *
     * @param password 密码明文
     * @return null 表示通过；否则返回弱密码原因
     */
    public static String weakPasswordReason(String password) {
        if (password == null || password.isEmpty()) {
            return "密码不能为空";
        }
        if (P_PWD_CONTINUOUS.matcher(password).find()) {
            return "密码不能包含5位及以上连续递增或递减数字";
        }
        if (P_PWD_REPEAT.matcher(password).find()) {
            return "密码不能包含3位及以上重复字符";
        }
        return null;
    }


    /**
     * 密码是否为弱密码
     */
    public static boolean isWeakPassword(String password) {
        return weakPasswordReason(password) != null;
    }

    // ==================== 私有工具方法 ====================

    private static boolean matches(Pattern pattern, String s) {
        return StringUtils.hasText(s) && pattern.matcher(s).matches();
    }

    private static ValidationResult check(boolean valid, String errorMsg) {
        return valid ? ValidationResult.ok() : ValidationResult.fail(errorMsg);
    }
}