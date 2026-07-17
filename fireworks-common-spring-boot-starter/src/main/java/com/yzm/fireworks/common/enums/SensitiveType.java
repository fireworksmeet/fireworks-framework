package com.yzm.fireworks.common.enums;

import com.yzm.fireworks.common.sensitive.ProcessSensitive;
import com.yzm.fireworks.common.util.ValidationUtil;
import org.springframework.util.StringUtils;

/**
 * @author JYuan
 * 脱敏类型枚举
 */
public enum SensitiveType implements ProcessSensitive {

    // ==================== 基础个人信息 ====================

    /**
     * 姓名：保留第一个字，其余替换为 *
     * 示例：张三 → 张*，张三丰 → 张**
     * 特殊：单字名（如"张"）→ *
     */
    NAME {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str)) {
                return str;
            }
            // 单字名直接全隐藏
            if (str.length() == 1) {
                return "*";
            }
            // 保留第一个字，其余隐藏
            return hide(str, 1, str.length());
        }
    },

    /**
     * 手机号：保留前3位和后4位
     * 示例：13812345678 → 138****5678
     */
    PHONE {
        @Override
        public boolean match(String str) {
            // 匹配手机号正则
            return ValidationUtil.isMobile(str);
        }

        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str) || str.length() < 8) {
                return str;
            }
            return hide(str, 3, str.length() - 4);
        }
    },

    /**
     * 身份证号：保留前3位和后4位（修复原逻辑）
     * 示例：110101199001011234 → 110***********1234
     */
    ID_NUMBER {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str) || str.length() < 8) {
                return str;
            }
            return hide(str, 3, str.length() - 4);
        }
    },

    /**
     * 银行卡号：保留前4位和后4位
     * 示例：6222021001122334455 → 6222***********4455
     */
    BANK_CARD {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str) || str.length() < 9) {
                return str;
            }
            return hide(str, 4, str.length() - 4);
        }
    },

    // ==================== 联系方式 ====================

    /**
     * 电子邮箱：'@' 前保留前1位，其余用 * 替代
     * 示例：example@gmail.com → e******@gmail.com
     */
    EMAIL {
        @Override
        public boolean match(String str) {
            // 匹配手机号正则
            return ValidationUtil.isEmail(str);
        }

        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str)) {
                return str;
            }
            int atIndex = str.indexOf('@');
            if (atIndex <= 1) {
                return str;
            }
            return hide(str, 1, atIndex);
        }
    },

    /**
     * 固定电话：保留区号（前4位）和后2位
     * 示例：01012345678 → 0101*****78
     */
    FIXED_PHONE {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str) || str.length() < 7) {
                return str;
            }
            return hide(str, 4, str.length() - 2);
        }
    },

    // ==================== 地址类 ====================

    /**
     * 详细地址：保留前6个字符（省市区），其余隐藏
     * 示例：北京市朝阳区建国路88号 → 北京市朝阳**********
     */
    ADDRESS {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str)) {
                return str;
            }
            int keepLen = Math.min(6, str.length());
            return hide(str, keepLen, str.length());
        }
    },

    /**
     * IP 地址（IPv4）：保留前两段，隐藏后两段
     * 示例：192.168.1.100 → 192.168.*.*
     */
    IP_ADDRESS {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str)) {
                return str;
            }
            String[] parts = str.split("\\.", -1);
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
            // IPv6 或其他格式：只保留前半段
            int mid = str.length() / 2;
            return hide(str, mid, str.length());
        }
    },

    // ==================== 证件号码 ====================

    /**
     * 护照号码：保留前2位和后2位
     * 示例：E12345678 → E1*****78
     */
    PASSPORT {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str) || str.length() < 5) {
                return str;
            }
            return hide(str, 2, str.length() - 2);
        }
    },

    /**
     * 统一社会信用代码（企业）：保留前2位和后2位
     * 示例：91310000MA1FL4MQ43 → 91**************43
     */
    UNIFIED_CREDIT_CODE {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str) || str.length() < 5) {
                return str;
            }
            return hide(str, 2, str.length() - 2);
        }
    },

    /**
     * 车牌号：保留省份简称和末2位
     * 示例：京A12345 → 京A***45
     */
    CAR_LICENSE {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str) || str.length() < 4) {
                return str;
            }
            return hide(str, 2, str.length() - 2);
        }
    },

    // ==================== 账号安全 ====================

    /**
     * 密码：全部替换为固定长度的 *（不暴露真实长度）
     * 示例：anyPassword → ********
     */
    PASSWORD {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str)) {
                return str;
            }
            return "********";
        }
    },

    /**
     * 用户名/账号：保留前1位和后1位，中间隐藏
     * 示例：zhangsan → z******n
     */
    USERNAME {
        @Override
        public String process(String str) {
            if (!StringUtils.hasText(str)) {
                return str;
            }
            if (str.length() <= 2) {
                return "*".repeat(str.length());
            }
            return hide(str, 1, str.length() - 1);
        }
    },

    /**
     * 自定义：直接全部隐藏为等长 *
     * 适合不适合上述所有规则的敏感字段
     */
    CUSTOM_HIDE_ALL {
        @Override
        public String process(String str) {
            return hideAll(str);
        }
    };

    /**
     * 默认 match 返回 true，不需要匹配逻辑的类型不用重写
     *
     * @param str 原始字符串
     * @return 是否匹配
     */
    public boolean match(String str) {
        return true;
    }
}