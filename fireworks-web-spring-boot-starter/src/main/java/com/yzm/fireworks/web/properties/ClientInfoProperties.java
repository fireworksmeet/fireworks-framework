package com.yzm.fireworks.web.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户端信息分析配置
 *
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.web.client-info")
@Data
public class ClientInfoProperties {
    /**
     * 是否开启客户端信息分析
     */
    private boolean enabled = false;
}
