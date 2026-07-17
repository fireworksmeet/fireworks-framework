package com.yzm.fireworks.api.enums;

/**
 * 选项枚举接口
 * <p>
 * 实现此接口的枚举可被选项自动扫描机制发现并注册，
 * 通过统一接口返回给前端作为下拉选项、筛选条件等。
 * <p>
 * 不继承 MyBatis-Plus 的 {@code IEnum}，因此枚举在数据库中存储的是 name（如 "ANDROID"），
 * 而非 value（如 0）。前端交互仍使用 Integer value。
 * <p>
 * 使用方式：
 * <pre>
 * &#64;Getter
 * &#64;AllArgsConstructor
 * public enum Gender implements IOptionEnum {
 *     UNKNOWN(0, "未知"),
 *     MALE(1, "男"),
 *     FEMALE(2, "女"),
 *     ;
 *
 *     &#64;JsonValue
 *     private final Integer value;
 *     private final String text;
 * }
 * </pre>
 *
 * @author JYuan
 */
public interface IOptionEnum {

    /**
     * 获取枚举值（用于前端交互的数值标识）
     *
     * @return 枚举值
     */
    Integer getValue();

    /**
     * 获取枚举文本（用于前端选项的显示文本）
     *
     * @return 枚举文本
     */
    String getText();
}
