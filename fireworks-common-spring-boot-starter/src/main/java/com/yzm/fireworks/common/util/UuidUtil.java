package com.yzm.fireworks.common.util;

import lombok.experimental.UtilityClass;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * UUID 工具：框架内统一的随机标识生成入口。
 *
 * <p>只提供紧凑格式（无连字符）：日志里的 workerId、事件 ID、对象键、导出文件名都用同一形态，
 * 避免同一框架出现 32 位与 36 位两种 UUID（{@link UUID#fromString(String)} 不接受紧凑格式，
 * 需要反解时先按 8-4-4-4-12 插入连字符）。
 *
 * <p><b>不适用于业务编号</b>：订单号 / 会员号需要「短且趋势递增」，UUID 随机无序，
 * 这类场景请用 {@code fireworks-id} 的 {@code IdUtil.getId(key)}。
 *
 * @author JYuan
 */
@UtilityClass
public class UuidUtil {

    /**
     * 紧凑格式的最大长度（32 位十六进制）。
     */
    private static final int MAX_LENGTH = 32;

    /**
     * 32 位紧凑格式 UUID（版本 4，无连字符），如 {@code 1450421b6a4645cb8a203e498c6d6502}。
     */
    public static String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 取 {@link #uuid32()} 的前 {@code length} 位，用于 workerId 这类短标识。
     *
     * @param length 1~32
     */
    public static String shortUuid(int length) {
        Assert.isTrue(length > 0 && length <= MAX_LENGTH, "length 必须在 1~32 之间: " + length);
        return uuid32().substring(0, length);
    }
}
