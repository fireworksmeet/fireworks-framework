package com.yzm.fireworks.message.domain;

/**
 * 渠道实现类型。
 * <p>
 * 取值由框架定义、不可由配置扩展；业务自定义实现需要新的枚举值。
 *
 * @author JYuan
 */
public enum ProviderType {

    /**
     * 阿里云短信
     */
    ALIYUN,

    /**
     * 腾讯云短信
     */
    TENCENT,

    /**
     * SMTP 邮件
     */
    SMTP,

    /**
     * Nchan 实时推送
     */
    NCHAN
}
