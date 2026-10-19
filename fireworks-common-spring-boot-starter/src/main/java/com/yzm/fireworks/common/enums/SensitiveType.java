package com.yzm.fireworks.common.enums;

import com.yzm.fireworks.common.util.SensitiveUtil;

import java.util.function.UnaryOperator;

/**
 * 脱敏类型：只承担「声明 → 算法」的映射，具体算法见 {@link SensitiveUtil}。
 *
 * <p>作为 {@code @Sensitive} 的取值类型使用；需要直接脱敏时也可以调用
 * {@link #process(String)} 或直接用 {@link SensitiveUtil} 的对应方法。
 *
 * @author JYuan
 */
public enum SensitiveType {

    /**
     * 姓名：张三 → 张*
     */
    NAME(SensitiveUtil::name),

    /**
     * 手机号：13812345678 → 138****5678
     */
    PHONE(SensitiveUtil::phone),

    /**
     * 身份证号：110101199001011234 → 110***********1234
     */
    ID_NUMBER(SensitiveUtil::idNumber),

    /**
     * 银行卡号：6222021001122334455 → 6222***********4455
     */
    BANK_CARD(SensitiveUtil::bankCard),

    /**
     * 电子邮箱：example@gmail.com → e******@gmail.com
     */
    EMAIL(SensitiveUtil::email),

    /**
     * 固定电话：01012345678 → 0101*****78
     */
    FIXED_PHONE(SensitiveUtil::fixedPhone),

    /**
     * 详细地址：北京市朝阳区建国路88号 → 北京市朝阳**********
     */
    ADDRESS(SensitiveUtil::address),

    /**
     * IP 地址：192.168.1.100 → 192.168.*.*
     */
    IP_ADDRESS(SensitiveUtil::ipAddress),

    /**
     * 护照号码：E12345678 → E1*****78
     */
    PASSPORT(SensitiveUtil::passport),

    /**
     * 统一社会信用代码：91310000MA1FL4MQ43 → 91**************43
     */
    UNIFIED_CREDIT_CODE(SensitiveUtil::unifiedCreditCode),

    /**
     * 车牌号：京A12345 → 京A***45
     */
    CAR_LICENSE(SensitiveUtil::carLicense),

    /**
     * 密码：anyPassword → ********
     */
    PASSWORD(SensitiveUtil::password),

    /**
     * 用户名/账号：zhangsan → z******n
     */
    USERNAME(SensitiveUtil::username),

    /**
     * 全部隐藏为等长 *，适用于不符合以上任何规则的敏感字段
     */
    CUSTOM_HIDE_ALL(SensitiveUtil::hideAll);

    private final UnaryOperator<String> processor;

    SensitiveType(UnaryOperator<String> processor) {
        this.processor = processor;
    }

    /**
     * 按本类型对字符串脱敏。
     *
     * @param str 原始字符串
     * @return 脱敏后的字符串；{@code null} / 空串原样返回
     */
    public String process(String str) {
        return processor.apply(str);
    }
}
