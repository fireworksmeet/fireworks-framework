package com.yzm.fireworks.xxljob;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
    @ConditionalOnMissingBean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        Assert.hasText(properties.getAdmin().getAddresses(),
                "fireworks.xxljob.admin.addresses 必须配置，否则无法与调度中心通信");
        Assert.hasText(properties.getExecutor().getAppName(),
                "fireworks.xxljob.executor.app-name 必须配置，否则无法自动注册到调度中心");

        log.info(">>>>>>>>>>> xxl-job config init");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(properties.getAdmin().getAddresses());
        xxlJobSpringExecutor.setAppname(properties.getExecutor().getAppName());
        xxlJobSpringExecutor.setAddress(properties.getExecutor().getAddress());
        // 未显式配置时不调用 setIp：xxl-job 内部会用 IPTool.getIp() 自行探测，
        // 因此本模块不需要 spring-cloud 的 InetUtils。
        String ip = properties.getExecutor().getIp();
        if (StringUtils.hasText(ip)) {
            xxlJobSpringExecutor.setIp(ip);
        }
        xxlJobSpringExecutor.setPort(properties.getExecutor().getPort());
        xxlJobSpringExecutor.setAccessToken(properties.getAccessToken());
        xxlJobSpringExecutor.setLogPath(properties.getExecutor().getLogPath());
        xxlJobSpringExecutor.setLogRetentionDays(properties.getExecutor().getLogRetentionDays());
        log.info("<<<<<<<<<<< xxl-job config finish");
        return xxlJobSpringExecutor;
    }
}
