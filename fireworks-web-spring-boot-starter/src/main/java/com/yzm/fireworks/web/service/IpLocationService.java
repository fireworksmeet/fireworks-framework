package com.yzm.fireworks.web.service;

import com.yzm.fireworks.web.properties.IpLocationProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.InvalidConfigException;
import org.lionsoul.ip2region.service.Ip2Region;
import org.lionsoul.ip2region.xdb.XdbException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

import static com.yzm.fireworks.common.constants.StringPool.*;


/**
 * IP地理位置服务
 *
 * @author JYuan
 */
@Slf4j
public class IpLocationService implements DisposableBean {

    private Ip2Region ip2Region;

    public IpLocationService(IpLocationProperties properties) {
        try {
            Resource v4Resource = getResource(properties.getV4XdbPath());
            Resource v6Resource = getResource(properties.getV6XdbPath());
            try (InputStream v4InputStream = v4Resource.getInputStream();
                 InputStream v6InputStream = v6Resource.getInputStream()) {
                Config v4Config = Config.custom()
                        .setCachePolicy(Config.BufferCache)
                        .setSearchers(properties.getSearcherNum())
                        .setXdbInputStream(v4InputStream)
                        .asV4();

                Config v6Config = Config.custom()
                        .setCachePolicy(Config.BufferCache)
                        .setSearchers(properties.getSearcherNum())
                        .setXdbInputStream(v6InputStream)
                        .asV6();

                this.ip2Region = Ip2Region.create(v4Config, v6Config);
            }
        } catch (IOException | XdbException | InvalidConfigException e) {
            log.error("IP地址库加载失败", e);
        }
    }

    private Resource getResource(String path) {
        if (path.startsWith(SLASH) || path.contains(COLON)) {
            return new FileSystemResource(path);
        }
        return new ClassPathResource(path);
    }

    /**
     * 根据IP获取地理位置信息
     */
    public LocationInfo getLocation(String ip) {
        if (ObjectUtils.isEmpty(ip2Region)) {
            log.warn("IP地址库未初始化");
            return buildUnknownLocation();
        }

        if (!StringUtils.hasText(ip) || UNKNOWN.equalsIgnoreCase(ip)) {
            return buildUnknownLocation();
        }

        // 过滤本地IP
        if (isLocalIp(ip)) {
            return buildLocalLocation();
        }

        try {
            String info = ip2Region.search(ip);
            return parseLocation(info);
        } catch (Exception e) {
            log.error("IP解析失败: {}", ip, e);
            return buildUnknownLocation();
        }
    }

    /**
     * 解析地理位置信息
     */
    private LocationInfo parseLocation(String info) {
        if (!StringUtils.hasText(info)) {
            return buildUnknownLocation();
        }

        String[] parts = info.split("\\|", -1);

        return LocationInfo.builder()
                .country(getValue(parts, 0))
                .province(getValue(parts, 1))
                .city(getValue(parts, 2))
                .isp(getValue(parts, 3))
                .build();
    }

    private String getValue(String[] parts, int index) {
        if (index >= parts.length) {
            return UNKNOWN;
        }
        String val = parts[index].trim();
        return "0".equals(val) ? UNKNOWN : val;
    }

    private boolean isLocalIp(String ip) {
        return LOCALHOST_IPV4.equals(ip)
                || LOCALHOST_IPV6.equals(ip)
                || LOCALHOST.equalsIgnoreCase(ip)
                || "::1".equals(ip)
                || ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || (ip.startsWith("172.") && isPrivateB(ip));
    }

    private boolean isPrivateB(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length >= 2) {
            try {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
    }

    private LocationInfo buildUnknownLocation() {
        return LocationInfo.builder()
                .country(UNKNOWN)
                .province(UNKNOWN)
                .city(UNKNOWN)
                .isp(UNKNOWN)
                .build();
    }

    private LocationInfo buildLocalLocation() {
        String local = "本地";
        return LocationInfo.builder()
                .country(local)
                .province(local)
                .city(local)
                .isp(local)
                .build();
    }

    @Override
    public void destroy() throws Exception {
        if (!ObjectUtils.isEmpty(ip2Region)) {
            ip2Region.close();
        }
    }

    /**
     * 地理位置信息
     */
    @Data
    @Builder
    public static class LocationInfo {
        private String country;      // 国家
        private String province;     // 省份
        private String city;         // 城市
        private String region;       // 区域
        private String isp;          // 运营商
        private String latitude;     // 纬度
        private String longitude;    // 经度
    }
}