package com.yzm.fireworks.webmvc.service;

import com.yzm.fireworks.webmvc.properties.IpLocationProperties;
import com.yzm.fireworks.webmvc.util.IpAddressUtils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Pattern;

import static com.yzm.fireworks.common.constants.StringPool.COLON;
import static com.yzm.fireworks.common.constants.StringPool.SLASH;
import static com.yzm.fireworks.common.constants.StringPool.UNKNOWN;

/**
 * IP 地理位置服务。
 * <p>
 * <b>启动即失败</b>：该 Bean 只在 {@code fireworks.webmvc.ip-location.enabled=true} 时创建，
 * 开启即代表业务依赖它，因此地址库缺失 / 损坏时直接让应用启动失败，而不是退化成「每次请求都查不到」
 * 的静默空转。
 *
 * @author JYuan
 */
@Slf4j
public class IpLocationService implements DisposableBean {

    private static final String LOCAL = "本地";

    /**
     * ISO 3166-1 alpha-2 国家码的形状：两个大写字母。
     */
    private static final Pattern ISO_COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    private static final LocationInfo UNKNOWN_LOCATION = LocationInfo.builder()
            .country(UNKNOWN).province(UNKNOWN).city(UNKNOWN).isp(UNKNOWN).countryCode(UNKNOWN)
            .build();

    private static final LocationInfo LOCAL_LOCATION = LocationInfo.builder()
            .country(LOCAL).province(LOCAL).city(LOCAL).isp(LOCAL).countryCode(UNKNOWN)
            .build();

    private final Ip2Region ip2Region;

    public IpLocationService(IpLocationProperties properties) {
        this.ip2Region = load(properties);
    }

    private Ip2Region load(IpLocationProperties properties) {
        Resource v4Resource = resolve(properties.getV4XdbPath());
        Resource v6Resource = resolve(properties.getV6XdbPath());
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

            return Ip2Region.create(v4Config, v6Config);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "IP 地址库加载失败，请检查 fireworks.webmvc.ip-location.v4-xdb-path / v6-xdb-path "
                            + "指向的 ip2region xdb 文件是否存在且完整（当前配置：v4=" + properties.getV4XdbPath()
                            + ", v6=" + properties.getV6XdbPath() + "）", e);
        }
    }

    /**
     * 解析地址库位置：支持 {@code classpath:} / {@code file:} 前缀，无前缀时绝对路径按文件系统处理，
     * 其余按 classpath 相对路径处理。
     */
    private Resource resolve(String path) {
        Assert.hasText(path, "IP 地址库路径不能为空");
        if (path.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX)) {
            return new ClassPathResource(path.substring(ResourceUtils.CLASSPATH_URL_PREFIX.length()));
        }
        if (path.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
            return new FileSystemResource(path.substring(ResourceUtils.FILE_URL_PREFIX.length()));
        }
        if (path.startsWith(SLASH) || path.contains(COLON)) {
            return new FileSystemResource(path);
        }
        return new ClassPathResource(path);
    }

    /**
     * 根据 IP 获取地理位置信息。
     * <p>
     * 非 IP 字面量（空、{@code UNKNOWN}、hostname）直接返回 {@code UNKNOWN}，不会触发地址库查询。
     */
    public LocationInfo getLocation(String ip) {
        String normalized = IpAddressUtils.normalize(ip);
        if (normalized == null) {
            return UNKNOWN_LOCATION;
        }
        if (IpAddressUtils.isLocal(normalized)) {
            return LOCAL_LOCATION;
        }
        try {
            return parseLocation(ip2Region.search(normalized));
        } catch (Exception e) {
            log.warn("IP 解析失败: {}", normalized, e);
            return UNKNOWN_LOCATION;
        }
    }

    /**
     * 解析 ip2region 3.x xdb 返回的 {@code 国家|省份|城市|ISP|ISO国家码} 串，
     * 例如 {@code 中国|广东省|深圳市|电信|CN}。
     * <p>
     * <b>没有「区域」字段</b>：那是 v1/v2 时代 {@code ip2region.db} 的 5 段格式
     * （{@code 国家|区域|省份|城市|ISP}），3.x 的数据已重新组织，第 2 段就是省份。
     * 第 5 段 ISO 国家码在部分数据中可能缺失，取不到时置为 {@code UNKNOWN}。
     */
    private LocationInfo parseLocation(String info) {
        if (!StringUtils.hasText(info)) {
            return UNKNOWN_LOCATION;
        }
        String[] parts = info.split("\\|", -1);
        return LocationInfo.builder()
                .country(getValue(parts, 0))
                .province(getValue(parts, 1))
                .city(getValue(parts, 2))
                .isp(getValue(parts, 3))
                .countryCode(getCountryCode(parts))
                .build();
    }

    /**
     * 第 5 段国家码；只接受两字母形式（大写），其余情况一律 {@code UNKNOWN}。
     * <p>
     * 做形状校验是因为 xdb 允许自定义追加字段，若业务用的是裁剪 / 扩展过的库，
     * 第 5 段未必是 ISO 码，不能盲目当成国家码使用。
     */
    private String getCountryCode(String[] parts) {
        String value = getValue(parts, 4);
        if (UNKNOWN.equals(value)) {
            return UNKNOWN;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return ISO_COUNTRY_CODE.matcher(upper).matches() ? upper : UNKNOWN;
    }

    private String getValue(String[] parts, int index) {
        if (index >= parts.length) {
            return UNKNOWN;
        }
        String val = parts[index].trim();
        return "0".equals(val) ? UNKNOWN : val;
    }

    @Override
    public void destroy() throws Exception {
        ip2Region.close();
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
        private String isp;          // 运营商
        /**
         * ISO 3166-1 alpha-2 国家码（如 {@code CN} / {@code US}），数据缺失或格式不符时为 {@code UNKNOWN}。
         * <p>
         * 该值取自 ip2region 数据第 5 段，做「两字母大写」形状校验后才返回；
         * 适合做语言 / 地区分流，<b>不适合作为合规或行政区划的唯一依据</b>。
         */
        private String countryCode;
    }
}
