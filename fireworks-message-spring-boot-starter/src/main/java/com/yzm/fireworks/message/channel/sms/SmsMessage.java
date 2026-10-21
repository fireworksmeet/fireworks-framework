package com.yzm.fireworks.message.channel.sms;

import com.yzm.fireworks.message.domain.ChannelMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 短信渠道入参。
 *
 * @author JYuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class SmsMessage implements ChannelMessage {

    /**
     * 消息标识；由门面从 {@code MessageEnvelope} 复制，用于构造结果。
     */
    private String messageId;

    /**
     * 手机号列表；必填。取 {@code Recipient.phoneNumbers} 原值（E.164 或国内 11 位），
     * 框架不改写；仅当厂商 API 强制 E.164 时由其实现自行补全国家码。
     */
    private List<String> phoneNumbers;

    /**
     * 短信签名；必填，来自配置 {@code fireworks.message.channels.sms.sign-name}。
     */
    private String signName;

    /**
     * 厂商模板 ID；必填，优先取 {@code MessageEnvelope.template}，否则取配置
     * {@code fireworks.message.channels.sms.template-id}。
     */
    private String templateId;

    /**
     * 厂商模板参数；腾讯云按 values 顺序填充，传入有序 Map 以避免参数错位。
     */
    private Map<String, Object> templateParams;
}
