package com.yzm.fireworks.msg.properties;

import com.yzm.fireworks.msg.enums.SmsProvider;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * 消息推送配置属性
 *
 * @author JYuan
 */
@Data
@Validated
@ConfigurationProperties(prefix = "fireworks.message.push")
public class MessageProperties {

    /**
     * 是否启用消息推送
     */
    private boolean enabled = false;

    /**
     * WebSocket 配置
     */
    private WebSocketConfig websocket = new WebSocketConfig();

    /**
     * 短信配置
     */
    private SmsConfig sms = new SmsConfig();

    /**
     * 邮件配置
     */
    private EmailConfig email = new EmailConfig();

    /**
     * 消息去重配置
     */
    private DeduplicationConfig deduplication = new DeduplicationConfig();

    /**
     * 异步发送配置
     */
    private AsyncConfig async = new AsyncConfig();

    @Data
    public static class AsyncConfig {

        /**
         * 是否启用异步线程池
         * <p>false 时 {@code sendAsync()} 降级为同步执行，便于测试环境复现问题
         */
        private boolean enabled = true;

        /**
         * 核心线程数
         * <p>推荐值：CPU 核心数，消息推送属于 IO 密集型任务可适当调大
         * <p>默认值 0 表示自动计算：CPU 核心数 + 1
         */
        @Min(0)
        private int corePoolSize;

        /**
         * 最大线程数
         * <p>默认值 0 表示自动计算：CPU 核心数 × 2
         */
        @Min(0)
        private int maxPoolSize;

        /**
         * 任务队列容量
         * <p>队列满后新任务会触发线程扩展直到 maxPoolSize；
         * 超出 maxPoolSize 后执行拒绝策略（默认：CallerRunsPolicy，在调用方线程同步执行）
         */
        @Min(1)
        private int queueCapacity = 200;

        /**
         * 线程空闲超时（秒），超出核心线程数的线程在空闲此时间后回收
         */
        @Min(1)
        private int keepAliveSeconds = 60;

        /**
         * 线程名前缀，便于日志和线程 dump 中定位
         */
        private String threadNamePrefix = "msg-async-";

        /**
         * 等待任务完成的最长时间（秒），超时后强制停止
         */
        @Min(1)
        private int awaitTerminationSeconds = 60;
    }

    @Data
    public static class WebSocketConfig {
        /**
         * 是否启用WebSocket推送
         */
        private boolean enabled = false;

        /**
         * Nchan允许channelId的最大字节数
         */
        private int nchanMaxChannelIdLength = 8192;

        /**
         * Nchan服务器地址
         */
        private String nchanUrl = "http://localhost:80";

        /**
         * 发布路径前缀
         */
        private String publishPrefix = "/pub";
    }

    @Data
    public static class SmsConfig {
        /**
         * 是否启用短信推送
         */
        private boolean enabled = false;

        /**
         * 短信服务商
         */
        private SmsProvider provider = SmsProvider.ALIYUN;

        /**
         * 阿里云配置
         */
        private AliyunConfig aliyun = new AliyunConfig();

        /**
         * 腾讯云配置
         */
        private TencentConfig tencent = new TencentConfig();

        /**
         * 默认签名
         */
        private String defaultSign;

        /**
         * 默认模板 ID
         */
        private String defaultTemplateId;

        /**
         * 限流配置
         */
        @NestedConfigurationProperty
        private RateLimitConfig rateLimit = new RateLimitConfig();

        public SmsConfig() {
            rateLimit.setKeyPrefix("sms:rate:limit:");
        }

        @Data
        public static class AliyunConfig {
            private String accessKeyId;
            private String accessKeySecret;
            private String endpoint = "dysmsapi.aliyuncs.com";
        }

        @Data
        public static class TencentConfig {
            private String secretId;
            private String secretKey;
            private String sdkAppId;
            private String endpoint = "sms.tencentcloudapi.com";
            private String region = "ap-shanghai";
        }
    }

    @Data
    public static class EmailConfig {
        /**
         * 是否启用邮件推送
         */
        private boolean enabled = false;

        /**
         * 默认发件人名称
         */
        private String defaultFromName;

        /**
         * 限流配置
         */
        @NestedConfigurationProperty
        private RateLimitConfig rateLimit = new RateLimitConfig();

        public EmailConfig() {
            rateLimit.setKeyPrefix("email:rate:limit:");
        }
    }

    @Data
    public static class RateLimitConfig {

        /**
         * 是否开启限流
         */
        private boolean enabled = true;

        /**
         * Redis key 前缀（必须包含冒号）
         */
        private String keyPrefix;

        /**
         * 窗口期内最大令牌数（容量）
         */
        @Min(1)
        private int limit = 1;

        /**
         * 限流时间窗口(秒)
         */
        @Min(1)
        private int windowSeconds = 60;
    }

    @Data
    public static class DeduplicationConfig {
        /**
         * 是否启用消息去重
         */
        private boolean enabled = true;

        /**
         * 去重时间窗口（秒）
         */
        @Min(1)
        private long windowSeconds = 300;

        /**
         * 处理中锁定的时间窗口（秒）
         * <p>初始占用 Key 时的极短过期时间，防止因系统异常导致去重 Key 无法释放而引起的死锁。
         * 发送成功后会转为正式的 windowSeconds。
         */
        @Min(1)
        private long processingWindowSeconds = 30;

        /**
         * Redis key前缀
         */
        private String redisKeyPrefix = "message:dedup:";
    }
}