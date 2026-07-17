package com.yzm.fireworks.msg.core.sender;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.msg.core.message.MessageResult;
import com.yzm.fireworks.msg.core.message.SmsMessage;
import com.yzm.fireworks.msg.core.ratelimit.RateLimiter;
import com.yzm.fireworks.msg.exception.MessagePushException;
import com.yzm.fireworks.msg.properties.MessageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static com.yzm.fireworks.common.constants.StringPool.COMMA;


/**
 * 阿里云短信发送实现
 *
 * @author JYuan
 */
@Slf4j
public class AliyunSmsSender extends AbstractRateLimitSender<SmsMessage> implements SmsSender {

    private final Client client;
    private final ObjectMapper objectMapper;

    public AliyunSmsSender(MessageProperties properties, ObjectMapper objectMapper,
                           RateLimiter rateLimiter) throws Exception {
        super(properties, rateLimiter);
        this.objectMapper = objectMapper;

        Config config = new Config()
                .setAccessKeyId(properties.getSms().getAliyun().getAccessKeyId())
                .setAccessKeySecret(properties.getSms().getAliyun().getAccessKeySecret())
                .setEndpoint(properties.getSms().getAliyun().getEndpoint());

        this.client = new Client(config);
    }

    @Override
    protected MessageResult doSend(SmsMessage message) throws Exception {
        String[] phoneNumbers = message.getPhoneNumbers();
        // 构建短信参数
        String templateParam = buildTemplateParam(message);
        String sign = StringUtils.hasText(message.getSmsSign()) ?
                message.getSmsSign() : properties.getSms().getDefaultSign();

        if (!StringUtils.hasText(sign)) {
            throw new MessagePushException("SignName is required for SMS");
        }

        String templateId = StringUtils.hasText(message.getTemplateId()) ?
                message.getTemplateId() : properties.getSms().getDefaultTemplateId();

        if (!StringUtils.hasText(templateId)) {
            throw new MessagePushException("templateId is required for SMS");
        }

        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(String.join(COMMA, phoneNumbers))
                .setSignName(sign)
                .setTemplateCode(templateId)
                .setTemplateParam(templateParam);

        SendSmsResponse response = client.sendSms(request);

        if ("OK".equals(response.getBody().getCode())) {
            log.info("SMS sent successfully: messageId={}, bizId={}",
                    message.getMessageId(), response.getBody().getBizId());

            MessageResult result = MessageResult.success(message.getMessageId());
            result.setExternalMessageId(response.getBody().getBizId());
            return result;
        } else {
            log.error("Failed to send SMS: code={}, message={}",
                    response.getBody().getCode(), response.getBody().getMessage());
            return MessageResult.failed(message.getMessageId(), response.getBody().getMessage());
        }
    }

    @Override
    protected String[] getRecipients(SmsMessage message) {
        return message.getPhoneNumbers();
    }

    @Override
    protected MessageProperties.RateLimitConfig getRateLimitConfig() {
        return properties.getSms().getRateLimit();
    }

    @Override
    protected String getChannelName() {
        return "Aliyun SMS";
    }

    /**
     * 构建短信模板参数
     */
    private String buildTemplateParam(SmsMessage message) {
        try {
            Map<String, Object> params = message.getTemplateParams();
            if (params == null) {
                params = new HashMap<>();
            }

            // 如果有内容但没有模板参数，尝试使用content作为默认参数
            if (message.getContent() != null && !params.containsKey("content")) {
                params.put("content", message.getContent());
            }

            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new MessagePushException("Failed to build template param", e);
        }
    }
}
