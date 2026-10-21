package com.yzm.fireworks.message.domain;

/**
 * 投递渠道类型。
 *
 * @author JYuan
 */
public enum ChannelType {

    /**
     * 短信
     */
    SMS,

    /**
     * 邮件
     */
    EMAIL,

    /**
     * 实时推送（经网关发布）
     */
    REALTIME
}
