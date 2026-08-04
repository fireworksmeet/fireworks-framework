package com.yzm.fireworks.web.aop;

import com.yzm.fireworks.api.enums.OptLogType;
import lombok.Builder;
import lombok.Getter;

/**
 * OptLog 注解元数据载体
 *
 * @author JYuan
 */
@Getter
@Builder
public class OptLogAttribute {
    /**
     * 所属模块，如 "用户管理"、"角色管理"
     */
    private final String module;

    /**
     * 操作类型
     */
    private final OptLogType type;

    /**
     * 日志描述，如 "新增系统用户"
     */
    private final String description;

    /**
     * 是否记录请求参数（JSON 序列化后存储），默认 true
     * 对于包含敏感信息（密码、Token）的接口可设为 false
     */
    private final boolean recordArgs;

    /**
     * 是否记录返回值（JSON 序列化后存储），默认 false
     * 返回值可能较大，按需开启
     */
    private final boolean recordResult;
}