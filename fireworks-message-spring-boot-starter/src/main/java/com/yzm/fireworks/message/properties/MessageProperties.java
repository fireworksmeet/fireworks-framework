package com.yzm.fireworks.message.properties;

import com.yzm.fireworks.message.domain.ChannelType;
import com.yzm.fireworks.message.domain.ProviderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 消息模块配置。
 *
 * @author JYuan
 */
@Data
@Validated
@ConfigurationProperties(prefix = "fireworks.message")
public class MessageProperties {

    /**
     * 是否启用消息模块
     */
    private boolean enabled = false;

    /**
     * 渠道配置
     */
    private Channels channels = new Channels();

    @Data
    public static class Channels {

        private Sms sms = new Sms();

        private Email email = new Email();

        private Realtime realtime = new Realtime();
    }

    @Data
    public static class Sms {

        /**
         * 是否启用短信渠道
         */
        private boolean enabled;

        /**
         * 实现选择；只有一个实现时可省略。
         */
        private ProviderType provider;

        /**
         * 默认签名
         */
        private String signName;

        /**
         * 默认厂商模板 code
         */
        private String templateId;

        private Aliyun aliyun = new Aliyun();

        private Tencent tencent = new Tencent();

        /**
         * 限流配置；为空表示不限流。
         */
        @Valid
        private RateLimit rateLimit;
    }

    @Data
    public static class Aliyun {

        private String accessKeyId;

        private String accessKeySecret;

        private String endpoint = "dysmsapi.aliyuncs.com";
    }

    @Data
    public static class Tencent {

        private String secretId;

        private String secretKey;

        private String sdkAppId;

        private String endpoint = "sms.tencentcloudapi.com";

        private String region = "ap-shanghai";
    }

    @Data
    public static class Email {

        /**
         * 是否启用邮件渠道
         */
        private boolean enabled;

        /**
         * 发件人地址；为空时取 spring.mail.username
         */
        private String from;

        /**
         * 发件人名称
         */
        private String fromName;

        /**
         * 限流配置；为空表示不限流。
         */
        @Valid
        private RateLimit rateLimit;
    }

    @Data
    public static class Realtime {

        /**
         * 是否启用实时渠道
         */
        private boolean enabled;

        @Valid
        private Nchan nchan = new Nchan();

        /**
         * 限流配置；为空表示不限流。
         */
        @Valid
        private RateLimit rateLimit;
    }

    @Data
    public static class Nchan {

        /**
         * Nchan 基础地址；仅用于拼接消息里的相对 path，绝对 URL 不受其影响。
         */
        private String url = "http://localhost:80";

        /**
         * 频道 ID 分隔符；必须与 Nginx 的 {@code nchan_channel_id_split_delimiter} 一致。
         * <p>单个可打印 ASCII 字符，默认 {@code ,}。
         */
        private String channelIdDelimiter = ",";

        /**
         * 单次 publish 的频道串最大字节数
         */
        @Min(1)
        private int maxChannelPathBytes = 4096;

        /**
         * 单次 publish 的最大频道数
         */
        @Min(1)
        private int maxChannelsPerPublish = 200;
    }

    @Data
    public static class RateLimit {

        /**
         * 窗口内允许的最大发送次数
         */
        @Min(1)
        private long limit;

        /**
         * 窗口长度，如 60s
         */
        private Duration window;
    }

    /**
     * 读取渠道的限流配置。
     * <p>
     * 未配置、或窗口/次数不完整时返回 {@code null}，表示不限制。
     *
     * @param channel 渠道
     * @return 有效配置或 {@code null}
     */
    public RateLimit rateLimitOf(ChannelType channel) {
        RateLimit rateLimit = switch (channel) {
            case SMS -> channels.getSms().getRateLimit();
            case EMAIL -> channels.getEmail().getRateLimit();
            case REALTIME -> channels.getRealtime().getRateLimit();
        };
        if (rateLimit == null || rateLimit.getLimit() <= 0 || rateLimit.getWindow() == null
                || rateLimit.getWindow().isZero() || rateLimit.getWindow().isNegative()) {
            return null;
        }
        return rateLimit;
    }
}
