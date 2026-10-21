package com.yzm.fireworks.xxljob;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author JYuan
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "fireworks.xxljob", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(XxlJobProperties.class)
@Slf4j
public class XxlJobAutoConfiguration {

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(InetUtils inetUtils, XxlJobProperties properties) {
        Assert.hasText(properties.getAdmin().getAddresses(),
                "fireworks.xxljob.admin.addresses 必须配置，否则无法与调度中心通信");
        Assert.hasText(properties.getExecutor().getAppName(),
                "fireworks.xxljob.executor.app-name 必须配置，否则无法自动注册到调度中心");

        log.info(">>>>>>>>>>> xxl-job config init");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(properties.getAdmin().getAddresses());
        xxlJobSpringExecutor.setAppname(properties.getExecutor().getAppName());
        xxlJobSpringExecutor.setAddress(properties.getExecutor().getAddress());
        String ip = properties.getExecutor().getIp();
        if (!StringUtils.hasText(ip)) {
            ip = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
        }
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(properties.getExecutor().getPort());
        xxlJobSpringExecutor.setAccessToken(properties.getAccessToken());
        xxlJobSpringExecutor.setLogPath(properties.getExecutor().getLogPath());
        xxlJobSpringExecutor.setLogRetentionDays(properties.getExecutor().getLogRetentionDays());
        log.info("<<<<<<<<<<< xxl-job config finish");
        return xxlJobSpringExecutor;
    }
}
