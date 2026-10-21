package com.yzm.fireworks.message.channel.sms;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20210111.models.SendStatus;
import com.yzm.fireworks.common.util.ExceptionUtil;
import com.yzm.fireworks.message.domain.ChannelResult;
import com.yzm.fireworks.message.domain.MessageEnvelope;
import com.yzm.fireworks.message.domain.ProviderType;
import com.yzm.fireworks.message.exception.MessageValidationException;
import com.yzm.fireworks.message.properties.MessageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.yzm.fireworks.common.constants.StringPool.NETWORK_ERROR;
import static com.yzm.fireworks.common.constants.StringPool.UNKNOWN;
import java.util.regex.Pattern;

/**
 * 腾讯云短信实现。
 *
 * @author JYuan
 */
@Slf4j
public class TencentSmsProvider implements SmsProvider {

    private static final String SUCCESS_CODE = "Ok";

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    private static final Pattern DOMESTIC = Pattern.compile("1[3-9]\\d{9}");

    private static final String DOMESTIC_PREFIX = "+86";

    private final SmsClient client;
    private final MessageProperties.Sms config;

    public TencentSmsProvider(MessageProperties properties) {
        this.config = properties.getChannels().getSms();
        Credential credential = new Credential(
                config.getTencent().getSecretId(),
                config.getTencent().getSecretKey());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(config.getTencent().getEndpoint());
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        this.client = new SmsClient(credential, config.getTencent().getRegion(), clientProfile);
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.TENCENT;
    }

    @Override
    public SmsMessage convert(MessageEnvelope envelope) {
        SmsMessage.SmsMessageBuilder builder = SmsMessageFactory.from(envelope, config);
        if (!StringUtils.hasText(config.getTencent().getSdkAppId())) {
            throw new MessageValidationException("sdkAppId is required for Tencent SMS");
        }
        return builder.phoneNumbers(toE164(envelope.getRecipient().getPhoneNumbers())).build();
    }

    @Override
    public ChannelResult send(SmsMessage message) {
        try {
            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumberSet(message.getPhoneNumbers().toArray(new String[0]));
            request.setSmsSdkAppId(config.getTencent().getSdkAppId());
            request.setSignName(message.getSignName());
            request.setTemplateId(message.getTemplateId());
            request.setTemplateParamSet(buildTemplateParams(message));

            SendSmsResponse response = client.SendSms(request);
            SendStatus[] statuses = response.getSendStatusSet();
            if (statuses != null && statuses.length > 0 && SUCCESS_CODE.equals(statuses[0].getCode())) {
                return new ChannelResult.Success(message.getMessageId(), statuses[0].getSerialNo());
            }

            String code = statuses == null || statuses.length == 0 ? UNKNOWN : statuses[0].getCode();
            String detail = statuses == null || statuses.length == 0 ? "Empty send status" : statuses[0].getMessage();
            log.warn("Tencent SMS rejected: messageId={}, code={}, message={}",
                    message.getMessageId(), code, detail);
            return failure(message.getMessageId(), code, detail);
        } catch (Exception e) {
            return classify(message.getMessageId(), e);
        }
    }

    /**
     * 腾讯云只接受 E.164，调用方允许传国内 11 位，故此处补 {@code +86}；其余格式原样交给腾讯云判定。
     */
    private static List<String> toE164(List<String> phoneNumbers) {
        return phoneNumbers.stream().map(TencentSmsProvider::normalize).toList();
    }

    private static String normalize(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new MessageValidationException("phoneNumber must not be blank");
        }
        if (E164.matcher(phone).matches()) {
            return phone;
        }
        if (DOMESTIC.matcher(phone).matches()) {
            return DOMESTIC_PREFIX + phone;
        }
        throw new MessageValidationException(
                "phoneNumber must be E.164 (e.g. +8613800000000) or a domestic 11-digit number: " + phone);
    }

    private String[] buildTemplateParams(SmsMessage message) {
        Map<String, Object> params = message.getTemplateParams();
        if (params == null || params.isEmpty()) {
            return new String[0];
        }
        List<String> values = new ArrayList<>(params.size());
        for (Object value : params.values()) {
            values.add(value == null ? "" : value.toString());
        }
        return values.toArray(new String[0]);
    }

    private ChannelResult failure(String messageId, String code, String detail) {
        if (isRetryable(code)) {
            return new ChannelResult.RetryableFailure(messageId, code, detail, null);
        }
        return new ChannelResult.PermanentFailure(messageId, code, detail);
    }

    private ChannelResult classify(String messageId, Exception e) {
        if (ExceptionUtil.hasCause(e, IOException.class)) {
            return new ChannelResult.RetryableFailure(messageId, NETWORK_ERROR, e.getMessage(), e);
        }
        if (e instanceof TencentCloudSDKException sdkException) {
            String code = sdkException.getErrorCode();
            if (isRetryable(code)) {
                return new ChannelResult.RetryableFailure(messageId, code, sdkException.getMessage(), e);
            }
            return new ChannelResult.PermanentFailure(messageId, code, sdkException.getMessage());
        }
        return new ChannelResult.RetryableFailure(messageId, UNKNOWN, e.getMessage(), e);
    }

    private boolean isRetryable(String code) {
        if (code == null) {
            return false;
        }
        return code.startsWith("LimitExceeded")
                || code.startsWith("InternalError")
                || code.startsWith("RequestLimitExceeded")
                || code.startsWith("FailedOperation.SystemError");
    }
}
