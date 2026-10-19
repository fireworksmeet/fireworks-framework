package com.yzm.fireworks.webmvc.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户端信息分析配置
 *
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.webmvc.client-info")
@Data
public class ClientInfoProperties {
    /**
     * 是否开启客户端信息分析
     */
    private boolean enabled = false;
}
