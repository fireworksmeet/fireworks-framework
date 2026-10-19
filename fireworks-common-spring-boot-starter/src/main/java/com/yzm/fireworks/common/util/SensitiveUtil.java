package com.yzm.fireworks.common.util;

import org.springframework.util.StringUtils;

/**
 * 脱敏算法工具类。
 *
 * <p>只做「原始字符串 → 脱敏字符串」的纯计算，不读注解、不依赖序列化，因此可以在任何场景直接调用
 * （日志打印、导出、Mock 数据、DTO 组装）。
 *
 * <p>与字段声明式脱敏的分工：{@code @Sensitive} + {@code SensitiveModule} 负责「对外序列化时自动脱敏」，
 * 本类负责「需要脱敏时随时可调」。两处共用同一套算法，避免规则分叉。
 *
 * <p>所有方法对 {@code null} 与空串原样返回，不抛异常。
 *
 * @author JYuan
 */
public final class SensitiveUtil {

    private static final char MASK_CHAR = '*';

    private SensitiveUtil() {
    }

    // ==================== 基础个人信息 ====================

    /**
     * 姓名：保留第一个字，其余替换为 *。
     * <p>示例：张三 → 张*，张三丰 → 张**；单字名 → *
     */
    public static String name(String str) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        if (str.length() == 1) {
            return String.valueOf(MASK_CHAR);
        }
        return hide(str, 1, str.length());
    }

    /**
     * 手机号：保留前 3 位和后 4 位。
     * <p>示例：13812345678 → 138****5678
     */
    public static String phone(String str) {
        if (!StringUtils.hasText(str) || str.length() < 8) {
            return str;
        }
        return hide(str, 3, str.length() - 4);
    }

    /**
     * 身份证号：保留前 3 位和后 4 位。
     * <p>示例：110101199001011234 → 110***********1234
     */
    public static String idNumber(String str) {
        return phone(str);
    }

    /**
     * 银行卡号：保留前 4 位和后 4 位。
     * <p>示例：6222021001122334455 → 6222***********4455
     */
    public static String bankCard(String str) {
        if (!StringUtils.hasText(str) || str.length() < 9) {
            return str;
        }
        return hide(str, 4, str.length() - 4);
    }

    // ==================== 联系方式 ====================

    /**
     * 电子邮箱：'@' 前保留前 1 位，其余用 * 替代。
     * <p>示例：example@gmail.com → e******@gmail.com
     */
    public static String email(String str) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        int atIndex = str.indexOf('@');
        if (atIndex <= 1) {
            return str;
        }
        return hide(str, 1, atIndex);
    }

    /**
     * 固定电话：保留区号（前 4 位）和后 2 位。
     * <p>示例：01012345678 → 0101*****78
     */
    public static String fixedPhone(String str) {
        if (!StringUtils.hasText(str) || str.length() < 7) {
            return str;
        }
        return hide(str, 4, str.length() - 2);
    }

    // ==================== 地址类 ====================

    /**
     * 详细地址：保留前 6 个字符（省市区），其余隐藏。
     * <p>示例：北京市朝阳区建国路88号 → 北京市朝阳**********
     */
    public static String address(String str) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        int keepLength = Math.min(6, str.length());
        return hide(str, keepLength, str.length());
    }

    /**
     * IP 地址：IPv4 保留前两段；其他格式保留前半段。
     * <p>示例：192.168.1.100 → 192.168.*.*
     */
    public static String ipAddress(String str) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        String[] parts = str.split("\\.", -1);
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return hide(str, str.length() / 2, str.length());
    }

    // ==================== 证件号码 ====================

    /**
     * 护照号码：保留前 2 位和后 2 位。
     * <p>示例：E12345678 → E1*****78
     */
    public static String passport(String str) {
        if (!StringUtils.hasText(str) || str.length() < 5) {
            return str;
        }
        return hide(str, 2, str.length() - 2);
    }

    /**
     * 统一社会信用代码（企业）：保留前 2 位和后 2 位。
     * <p>示例：91310000MA1FL4MQ43 → 91**************43
     */
    public static String unifiedCreditCode(String str) {
        return passport(str);
    }

    /**
     * 车牌号：保留省份简称和末 2 位。
     * <p>示例：京A12345 → 京A***45
     */
    public static String carLicense(String str) {
        if (!StringUtils.hasText(str) || str.length() < 4) {
            return str;
        }
        return hide(str, 2, str.length() - 2);
    }

    // ==================== 账号安全 ====================

    /**
     * 密码：全部替换为固定长度的 *，不暴露真实长度。
     * <p>示例：anyPassword → ********
     */
    public static String password(String str) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        return String.valueOf(MASK_CHAR).repeat(8);
    }

    /**
     * 用户名/账号：保留前 1 位和后 1 位，中间隐藏。
     * <p>示例：zhangsan → z******n
     */
    public static String username(String str) {
        if (!StringUtils.hasText(str)) {
            return str;
        }
        if (str.length() <= 2) {
            return String.valueOf(MASK_CHAR).repeat(str.length());
        }
        return hide(str, 1, str.length() - 1);
    }

    /**
     * 全部隐藏为等长 *，用于不适合任何已知规则的敏感字段。
     */
    public static String hideAll(String str) {
        return StringUtil.maskAll(str, MASK_CHAR);
    }

    /**
     * 将 [start, end) 区间内的字符替换为 *。
     *
     * @return 参数非法或字符串为空时原样返回，见 {@link StringUtil#mask(String, int, int)}
     */
    private static String hide(String str, int start, int end) {
        return StringUtil.mask(str, start, end, MASK_CHAR);
    }
}
