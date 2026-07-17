package com.yzm.fireworks.web.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IP地理位置服务配置
 *
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.web.ip-location")
@Data
public class IpLocationProperties {
    /**
     * 是否开启IP地理位置服务
     */
    private boolean enabled = false;

    /**
     * IPv4地址库路径（classpath相对路径或绝对路径）
     */
    private String v4XdbPath = "ip2region/ip2region_v4.xdb";

    /**
     * IPv6地址库路径（classpath相对路径或绝对路径）
     */
    private String v6XdbPath = "ip2region/ip2region_v6.xdb";

    /**
     * 搜索器数量
     */
    private int searcherNum = 15;
}
