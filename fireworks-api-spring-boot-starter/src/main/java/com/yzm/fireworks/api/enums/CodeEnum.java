package com.yzm.fireworks.api.enums;

import java.io.Serializable;

/**
 * 带业务编码与展示文案的枚举契约（<b>与任何 ORM 无关</b>）。
 * <p>
 * 实现类对外暴露 {@link #getCode()} 作为该枚举的持久化 / 传输编码
 * （如 {@code 0}），使「入库值」「前端交互值」「JSON 序列化值」三处保持一致；
 * {@link #getLabel()} 提供面向用户的展示文案。二者构成字典项的标准配对
 * （{@code code} + {@code label}）。
 * <p>
 * 【为什么不提供 {@code getLabel()} 的默认实现】默认返回 {@code name()} 会让"忘了写文案"
 * 静默通过，而 {@code name()} 通常不是给用户看的文字（如 {@code NORMAL}）。
 * 枚举普遍使用 {@code @Getter} + 字段，实现成本为零。
 * <p>
 * 使用方式：
 * <pre>{@code
 * @Getter
 * @AllArgsConstructor
 * public enum Gender implements CodeEnum<Integer> {
 *     UNKNOWN(0, "未知"),
 *     MALE(1, "男"),
 *     FEMALE(2, "女"),
 *     ;
 *
 *     @JsonValue
 *     private final Integer code;
 *     private final String label;
 * }
 * }</pre>
 * 需要暴露为前端字典项时，额外标注 {@link ExposedEnum}。
 *
 * @author JYuan
 */
public interface CodeEnum<V extends Serializable> {

    /**
     * 枚举的业务编码。
     *
     * @return 业务编码
     */
    V getCode();

    /**
     * 面向用户的展示文案。
     *
     * @return 展示文案
     */
    String getLabel();
}
