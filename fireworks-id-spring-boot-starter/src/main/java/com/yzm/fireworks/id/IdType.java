package com.yzm.fireworks.id;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author JYuan
 */
@Getter
@AllArgsConstructor
public enum IdType {

    /**
     * 已经存储数据库的号段
     * ORDER: 用于订单(起始值12位)
     * MEMBER: 用户会员(起始值9位)
     * FILE_SUFFIX: 用于文件后缀(起始值14位)
     * SERIAL_NUMBER: 通用流水号(起始值16位)
     */
    ORDER("ORDER"),
    MEMBER("MEMBER"),
    FILE_SUFFIX("FILE_SUFFIX"),
    SERIAL_NUMBER("SERIAL_NUMBER"),
    ;

    private final String value;

    public static IdType match(String val, IdType def) {
        for (IdType enm : IdType.values()) {
            if (enm.value.equals(val)) {
                return enm;
            }
        }
        return def;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static IdType get(@JsonProperty("value") String value) {
        return match(value, null);
    }

}
