package com.yzm.fireworks.message.channel.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.common.util.ExceptionUtil;
import com.yzm.fireworks.message.domain.ChannelResult;
import com.yzm.fireworks.message.domain.MessageEnvelope;
import com.yzm.fireworks.message.domain.ProviderType;
import com.yzm.fireworks.message.properties.MessageProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Set;

import static com.yzm.fireworks.common.constants.StringPool.NETWORK_ERROR;
import static com.yzm.fireworks.common.constants.StringPool.UNKNOWN;

/**
 * 阿里云短信实现。
 *
 * @author JYuan
 */
@Slf4j
public class AliyunSmsProvider implements SmsProvider {

    private static final String SUCCESS_CODE = "OK";

    /**
     * 可重试的厂商码：系统错误、服务不可用、触发流控。
     */
    private static final Set<String> RETRYABLE_CODES = Set.of(
            "isp.SYSTEM_ERROR",
            "isp.SERVICE_UNAVAILABLE",
            "isv.BUSINESS_LIMIT_CONTROL",
            "Throttling",
            "Throttling.User"
    );

    private final Client client;
    private final MessageProperties.Sms config;
    private final ObjectMapper objectMapper;

    public AliyunSmsProvider(MessageProperties properties, ObjectMapper objectMapper) {
        this.config = properties.getChannels().getSms();
        this.objectMapper = objectMapper;
        Config sdkConfig = new Config()
                .setAccessKeyId(config.getAliyun().getAccessKeyId())
                .setAccessKeySecret(config.getAliyun().getAccessKeySecret())
                .setEndpoint(config.getAliyun().getEndpoint());
        try {
            this.client = new Client(sdkConfig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Aliyun SMS client", e);
        }
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.ALIYUN;
    }

    @Override
    public SmsMessage convert(MessageEnvelope envelope) {
        return SmsMessageFactory.from(envelope, config).build();
    }

    @Override
    public ChannelResult send(SmsMessage message) {
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(String.join(",", message.getPhoneNumbers()))
                    .setSignName(message.getSignName())
                    .setTemplateCode(message.getTemplateId())
                    .setTemplateParam(objectMapper.writeValueAsString(message.getTemplateParams()));

            SendSmsResponse response = client.sendSms(request);
            String code = response.getBody().getCode();
            if (SUCCESS_CODE.equals(code)) {
                return new ChannelResult.Success(message.getMessageId(), response.getBody().getBizId());
            }
            log.warn("Aliyun SMS rejected: messageId={}, code={}, message={}",
                    message.getMessageId(), code, response.getBody().getMessage());
            return failure(message.getMessageId(), code, response.getBody().getMessage());
        } catch (Exception e) {
            return classify(message.getMessageId(), e);
        }
    }

    private ChannelResult failure(String messageId, String code, String detail) {
        if (RETRYABLE_CODES.contains(code)) {
            return new ChannelResult.RetryableFailure(messageId, code, detail, null);
        }
        return new ChannelResult.PermanentFailure(messageId, code, detail);
    }

    private ChannelResult classify(String messageId, Exception e) {
        if (ExceptionUtil.hasCause(e, IOException.class)) {
            return new ChannelResult.RetryableFailure(messageId, NETWORK_ERROR, e.getMessage(), e);
        }
        String code = extractCode(e);
        if (code != null && RETRYABLE_CODES.contains(code)) {
            return new ChannelResult.RetryableFailure(messageId, code, e.getMessage(), e);
        }
        if (code != null) {
            return new ChannelResult.PermanentFailure(messageId, code, e.getMessage());
        }
        return new ChannelResult.RetryableFailure(messageId, UNKNOWN, e.getMessage(), e);
    }

    private String extractCode(Exception e) {
        if (e instanceof com.aliyun.tea.TeaException teaException) {
            return teaException.getCode();
        }
        return null;
    }
}
