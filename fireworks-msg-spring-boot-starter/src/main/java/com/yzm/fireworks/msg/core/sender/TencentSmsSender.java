package com.yzm.fireworks.msg.core.sender;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import com.yzm.fireworks.msg.core.message.MessageResult;
import com.yzm.fireworks.msg.core.message.SmsMessage;
import com.yzm.fireworks.msg.core.ratelimit.RateLimiter;
import com.yzm.fireworks.msg.exception.MessagePushException;
import com.yzm.fireworks.msg.properties.MessageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 腾讯云短信发送实现
 *
 * @author JYuan
 */
@Slf4j
public class TencentSmsSender extends AbstractRateLimitSender<SmsMessage> implements SmsSender {

    private final SmsClient client;

    public TencentSmsSender(MessageProperties properties, RateLimiter rateLimiter) {
        super(properties, rateLimiter);

        Credential cred = new Credential(
                properties.getSms().getTencent().getSecretId(),
                properties.getSms().getTencent().getSecretKey()
        );

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(properties.getSms().getTencent().getEndpoint());

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        this.client = new SmsClient(cred, properties.getSms().getTencent().getRegion(), clientProfile);
    }

    @Override
    protected MessageResult doSend(SmsMessage message) throws Exception {
        String[] phoneNumbers = message.getPhoneNumbers();
        int length = phoneNumbers.length;

        SendSmsRequest request = new SendSmsRequest();

        // 格式化手机号（需要+86前缀）
        String[] formattedPhones = new String[length];
        for (int i = 0; i < length; i++) {
            formattedPhones[i] = phoneNumbers[i].startsWith("+86") ?
                    phoneNumbers[i] : "+86" + phoneNumbers[i];
        }
        request.setPhoneNumberSet(formattedPhones);

        String sdkAppId = properties.getSms().getTencent().getSdkAppId();
        if (!StringUtils.hasText(sdkAppId)) {
            throw new MessagePushException("SdkAppId is required for TencentSmsSender");
        }
        request.setSmsSdkAppId(sdkAppId);

        String sign = StringUtils.hasText(message.getSmsSign()) ?
                message.getSmsSign() : properties.getSms().getDefaultSign();
        if (!StringUtils.hasText(sign)) {
            throw new MessagePushException("SignName is required for SMS");
        }
        request.setSignName(sign);

        String templateId = StringUtils.hasText(message.getTemplateId()) ?
                message.getTemplateId() : properties.getSms().getDefaultTemplateId();
        if (!StringUtils.hasText(templateId)) {
            throw new MessagePushException("templateId is required for SMS");
        }
        request.setTemplateId(templateId);


        // 构建模板参数
        String[] templateParams = buildTemplateParams(message);
        request.setTemplateParamSet(templateParams);

        SendSmsResponse response = client.SendSms(request);
        SendStatus[] statuses = response.getSendStatusSet();

        if (!ObjectUtils.isEmpty(statuses) && "Ok".equals(statuses[0].getCode())) {
            log.info("SMS sent successfully: messageId={}, serialNo={}",
                    message.getMessageId(), statuses[0].getSerialNo());

            MessageResult result = MessageResult.success(message.getMessageId());
            result.setExternalMessageId(statuses[0].getSerialNo());
            return result;
        } else {
            String errorMsg = !ObjectUtils.isEmpty(statuses) ?
                    statuses[0].getMessage() : "Unknown error";
            log.error("Failed to send SMS: {}", errorMsg);
            return MessageResult.failed(message.getMessageId(), errorMsg);
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
        return "Tencent SMS";
    }

    /**
     * 构建短信模板参数数组
     */
    private String[] buildTemplateParams(SmsMessage message) {
        Map<String, Object> params = message.getTemplateParams();
        if (params == null || params.isEmpty()) {
            if (message.getContent() != null) {
                return new String[]{message.getContent()};
            }
            return new String[0];
        }

        List<String> paramList = new ArrayList<>();
        for (Object value : params.values()) {
            paramList.add(value != null ? value.toString() : "");
        }

        return paramList.toArray(new String[0]);
    }
}
