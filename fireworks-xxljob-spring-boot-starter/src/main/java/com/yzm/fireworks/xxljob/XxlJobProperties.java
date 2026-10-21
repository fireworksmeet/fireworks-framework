package com.yzm.fireworks.xxljob;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.xxljob")
@Data
public class XxlJobProperties {

    /**
     * 总开关[必填]，为 true 时执行器自动配置才会生效
     */
    private Boolean enabled;

    /**
     * 调度中心通信令牌[选填]，需与调度中心配置一致
     */
    private String accessToken;

    /**
     * 调度中心（Admin）配置
     */
    private Admin admin = new Admin();

    /**
     * 执行器配置
     */
    private Executor executor = new Executor();


    @Data
    public static class Admin {
        /**
         * 调度中心地址[必填]，多个地址用逗号分隔
         */
        private String addresses;
    }

    @Data
    public static class Executor {
        /**
         * 执行器AppName[必填]，作为自动注册到调度中心的分组标识；
         * 为空则无法注册，本 Starter 启动时会校验并要求配置
         */
        private String appName;


        /**
         * 执行器注册地址[选填]，为空则由 ip:port 自动生成（http://ip:port/）；
         * 多网卡、容器环境下自动探测的 IP 不可达时，可显式指定
         */
        private String address;

        /**
         * 执行器IP[选填]，为空则自动获取
         */
        private String ip;

        /**
         * 执行器端口号[选填]，小于等于0则自动获取
         */
        private int port;

        /**
         * 执行器日志路径[选填]，为空则使用默认路径
         */
        private String logPath;

        /**
         * 日志保存天数[选填]，值大于3时生效
         */
        private int logRetentionDays;
    }
}
