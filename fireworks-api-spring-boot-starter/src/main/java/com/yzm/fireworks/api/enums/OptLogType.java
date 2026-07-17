package com.yzm.fireworks.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.yzm.fireworks.api.annotation.OptLog;
import com.yzm.fireworks.api.util.EnumUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作日志类型枚举
 *
 * <p>用于 {@link OptLog#type()}，
 * 强约束操作类型取值，避免自由填写导致的数据不一致。
 *
 * @author JYuan
 */
@Getter
@AllArgsConstructor
@Schema(title = "操作日志类型", description = "0:新增, 1:修改, 2:删除, 3:查询, 4:导入, 5:导出, 6:授权, 7:强退, 8:清空, 9:其他")
public enum OptLogType implements IOptionEnum {

    /**
     * 操作日志类型
     */
    ADD(0, "新增"),
    UPDATE(1, "修改"),
    DELETE(2, "删除"),
    QUERY(3, "查询"),
    IMPORT(4, "导入"),
    EXPORT(5, "导出"),
    GRANT(6, "授权"),
    FORCE_LOGOUT(7, "强退"),
    CLEAR(8, "清空"),
    OTHER(9, "其他");

    @JsonValue
    private final Integer value;
    private final String text;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static OptLogType get(@JsonProperty("value") Integer value) {
        return EnumUtil.match(OptLogType.class, value, null);
    }
}
