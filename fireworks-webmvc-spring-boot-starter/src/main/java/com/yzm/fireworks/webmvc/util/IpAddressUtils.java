package com.yzm.fireworks.webmvc.util;

import com.google.common.net.InetAddresses;
import com.yzm.fireworks.common.constants.StringPool;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * IP 地址工具：解析、规范化、内网判断。
 * <p>
 * 不做 DNS 解析（{@link InetAddresses#forString(String)} 只接受字面量），因此不会因脏数据触发网络请求。
 *
 * @author JYuan
 */
public final class IpAddressUtils {

    private IpAddressUtils() {
    }

    /**
     * 解析 IP 字面量，非法输入返回 {@code null}。
     * <p>兼容 {@code [::1]} 这种带方括号的写法，并去掉 {@code fe80::1%eth0} 的 zone id。
     */
    public static InetAddress parse(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        String candidate = ip.trim();
        if (candidate.startsWith("[") && candidate.endsWith("]") && candidate.length() > 2) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        int zoneIndex = candidate.indexOf('%');
        if (zoneIndex > 0) {
            candidate = candidate.substring(0, zoneIndex);
        }
        try {
            return InetAddresses.forString(candidate);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 规范化为 IP 字面量：IPv4-mapped IPv6（{@code ::ffff:192.168.1.1}）还原为 IPv4 写法，
     * 其余保持原样。非法输入返回 {@code null}。
     */
    public static String normalize(String ip) {
        InetAddress address = parse(ip);
        if (address == null) {
            return null;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address && isMappedIpv4(bytes)) {
            return (bytes[12] & 0xFF) + StringPool.DOT + (bytes[13] & 0xFF) + StringPool.DOT + (bytes[14] & 0xFF) + StringPool.DOT + (bytes[15] & 0xFF);
        }
        return InetAddresses.toAddrString(address);
    }

    /**
     * 是否为内网 / 保留地址（不可路由到公网）。
     * <p>
     * 覆盖：任意地址（{@code 0.0.0.0}、{@code ::}）、环回、链路本地（{@code 169.254/16}、{@code fe80::/10}）、
     * 私有地址（{@code 10/8}、{@code 172.16/12}、{@code 192.168/16}）、CGNAT（{@code 100.64/10}）、
     * IPv6 ULA（{@code fc00::/7}）。
     */
    public static boolean isLocal(String ip) {
        InetAddress address = parse(ip);
        if (address == null) {
            return false;
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 100 && second >= 64 && second <= 127;
        }
        return (bytes[0] & 0xFE) == 0xFC;
    }

    /**
     * 是否为可路由的公网地址（即「合法且非内网」）。
     */
    public static boolean isPublic(String ip) {
        return parse(ip) != null && !isLocal(ip);
    }

    private static boolean isMappedIpv4(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

}
