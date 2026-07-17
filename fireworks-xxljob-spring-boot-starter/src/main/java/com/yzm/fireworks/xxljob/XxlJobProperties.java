package com.yzm.fireworks.xxljob;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.xxljob")
@Data
public class XxlJobProperties {

    private Boolean enabled;

    private String accessToken;

    private Admin admin;

    private Executor executor;


    @Data
    public static class Admin {
        private String addresses;
    }

    @Data
    public static class Executor {
        /**
         * 执行器AppName[选填]，为空则关闭自动注册
         */
        private String appName;


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
