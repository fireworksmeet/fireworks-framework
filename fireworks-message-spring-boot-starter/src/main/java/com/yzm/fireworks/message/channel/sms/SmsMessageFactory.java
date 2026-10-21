package com.yzm.fireworks.message.channel.sms;

import com.yzm.fireworks.message.domain.MessageEnvelope;
import com.yzm.fireworks.message.domain.Recipient;
import com.yzm.fireworks.message.exception.MessageValidationException;
import com.yzm.fireworks.message.properties.MessageProperties;
import lombok.experimental.UtilityClass;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 短信入参的公共校验与组装。
 * <p>
 * 两个厂商实现的必填校验、模板 ID 回退规则完全一致，集中在这里避免文案与规则各写一份后漂移；
 * 厂商差异（E.164 归一化、SDK 专属配置）留在各自实现中。
 *
 * @author JYuan
 */
@UtilityClass
class SmsMessageFactory {

    /**
     * 校验短信公共入参并组装：收件人、签名、模板 ID、模板参数。
     *
     * @param envelope 消息
     * @param config   短信渠道配置
     * @return 已填充公共字段的 builder，由调用方补充厂商差异
     * @throws MessageValidationException 公共必填项缺失
     */
    static SmsMessage.SmsMessageBuilder from(MessageEnvelope envelope, MessageProperties.Sms config) {
        Recipient recipient = envelope.getRecipient();
        if (recipient == null || CollectionUtils.isEmpty(recipient.getPhoneNumbers())) {
            throw new MessageValidationException("phoneNumbers is required for SMS");
        }
        if (!StringUtils.hasText(config.getSignName())) {
            throw new MessageValidationException("signName is required for SMS");
        }

        String templateId = StringUtils.hasText(envelope.getTemplate())
                ? envelope.getTemplate()
                : config.getTemplateId();
        if (!StringUtils.hasText(templateId)) {
            throw new MessageValidationException("templateId is required for SMS");
        }

        Map<String, Object> params = envelope.getParams() == null ? Map.of() : envelope.getParams();
        return SmsMessage.builder()
                .messageId(envelope.getMessageId())
                .phoneNumbers(recipient.getPhoneNumbers())
                .signName(config.getSignName())
                .templateId(templateId)
                .templateParams(params);
    }
}
