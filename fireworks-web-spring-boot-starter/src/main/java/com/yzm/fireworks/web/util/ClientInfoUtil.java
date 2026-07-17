package com.yzm.fireworks.web.util;

import com.yzm.fireworks.web.handler.UserAgentAnalyzerWarmupHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.util.DigestUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yzm.fireworks.common.constants.StringPool.*;


/**
 * 客户端信息工具类
 *
 * <p>当 {@code fireworks.web.client-info.enabled=true} 时，UA 解析功能可用；
 * 未开启时，UA 相关字段统一返回 {@code UNKNOWN}，不会触发 Yauaa 的重量级初始化。
 * IP 获取、请求信息等不依赖 Yauaa 的功能始终可用。
 *
 * @author JYuan
 */
@Slf4j
public class ClientInfoUtil {

    // ==================== 开关控制 ====================

    /**
     * UA 解析功能开关，由 {@code WebAutoConfiguration} 在 Spring 容器就绪时设置。
     * <p>
     * 默认 {@code false}，此时 {@link #parseUserAgent} 直接返回 null，
     * 不会触发 {@link AnalyzerHolder} 类加载，Yauaa 的 122 个 yaml 规则文件不会被读入内存。
     * -- SETTER --
     *  设置 UA 解析开关，仅供框架内部调用。
     * -- GETTER --
     *  UA 解析是否已开启
     */
    @Getter
    @Setter
    private static volatile boolean uaEnabled = false;

    // ==================== Yauaa 分析器（懒加载单例） ====================

    /**
     * 使用静态内部类实现懒加载单例（Initialization-on-demand Holder）。
     * <p>
     * 优化点：通过 {@code withField} 只声明实际使用的字段，Yauaa 仅加载
     * 对应的匹配规则子集，相比全量加载可显著降低初始化耗时和内存占用，
     * 并将单次解析耗时从 ~5ms 降低至 <1ms（未命中缓存时）。
     * <p>
     * 在 Spring 应用中应配合 {@link UserAgentAnalyzerWarmupHandler} 在
     * {@code ApplicationReadyEvent} 时异步预热，使初始化提前发生在启动阶段，
     * 而非第一个业务请求到来时。
     * <p>
     * <b>重要</b>：此类仅在 {@link #uaEnabled} 为 {@code true} 时才会被加载，
     * 未开启客户端信息功能时不会产生任何内存和启动耗时开销。
     */
    private static class AnalyzerHolder {
        static final UserAgentAnalyzer INSTANCE = UserAgentAnalyzer
                .newBuilder()
                // 隐藏加载统计
                .hideMatcherLoadStats()
                // 缓存 10000 条，命中缓存时解析耗时 < 1μs
                .withCache(10000)
                // ↓ 只声明实际使用的字段，避免全量 122 字段解析
                .withField("AgentName")
                .withField("AgentVersion")
                .withField("AgentClass")
                .withField("OperatingSystemName")
                .withField("OperatingSystemVersion")
                .withField("DeviceClass")
                .withField("DeviceBrand")
                .withField("DeviceName")
                .withField("LayoutEngineName")
                .withField("LayoutEngineVersion")
                .withField("AgentClass")
                .immediateInitialization()
                .build();
    }

    /**
     * 获取分析器实例，首次调用会触发 yaml 加载（约 5~15 秒）。
     * 正常情况下启动时已由 {@link UserAgentAnalyzerWarmupHandler} 完成预热。
     *
     * @throws IllegalStateException 如果 UA 解析未开启
     */
    public static UserAgentAnalyzer getAnalyzer() {
        if (!uaEnabled) {
            throw new IllegalStateException(
                    "UA 解析未开启，请设置 fireworks.web.client-info.enabled=true");
        }
        return AnalyzerHolder.INSTANCE;
    }

    // ==================== IP 获取 ====================

    public static String getClientIp(HttpServletRequest request) {
        if (ObjectUtils.isEmpty(request)) {
            return UNKNOWN;
        }

        // 依次尝试各种头部
        String[] headers = {
                "X-Real-IP",
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (isValidIp(ip)) {
                // 处理多个IP的情况,取第一个
                if (ip.contains(COMMA)) {
                    ip = ip.split(COMMA)[0].trim();
                }
                return ip;
            }
        }
        String ip = request.getRemoteAddr();
        return LOCALHOST_IPV6.equals(ip) ? LOCALHOST_IPV4 : ip;
    }

    private static boolean isValidIp(String ip) {
        return StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip);
    }

    // ==================== App 版本相关 Header 常量 ====================

    private static final String HEADER_APP_VERSION    = "App-Version";
    private static final String HEADER_X_APP_VERSION  = "X-App-Version";
    private static final String HEADER_CLIENT_VERSION = "Client-Version";
    private static final String HEADER_APP_PLATFORM   = "App-Platform";
    private static final String HEADER_APP_BUILD      = "App-Build";
    private static final String HEADER_APP_CHANNEL    = "App-Channel";

    private static final String UA_MINIPROGRAM_FLAG = "miniProgram";

    private static final Pattern UA_APP_VERSION_PATTERN =
            Pattern.compile("(?<![\\w./])([A-Za-z][\\w-]*/)(\\d+\\.\\d+[.\\d]*)");

    private static final Pattern UA_MINIPROGRAM_VERSION_PATTERN =
            Pattern.compile("miniProgram/([\\d.]+)");

    // ==================== 核心：获取完整客户端信息 ====================

    /**
     * 获取完整的客户端信息（推荐统一入口）。
     * <p>
     * 内部只调用一次 {@link #parseUserAgent}，所有字段均从同一个
     * {@link UserAgent} 实例中提取，避免多次重复解析同一 UA 字符串。
     * <p>
     * <b>注意</b>：业务层应只调用此方法，不要在外部单独调用
     * {@code getBrowser(request)}、{@code getDeviceType(request)} 等
     * 接收 {@code HttpServletRequest} 的重载，每次调用都会触发一次独立解析。
     */
    public static ClientInfo getClientInfo(HttpServletRequest request) {
        // 只解析一次
        UserAgent ua = parseUserAgent(request);

        return ClientInfo.builder()
                .ip(getClientIp(request))
                .userAgent(getUserAgentString(request))
                .browser(getBrowser(ua))
                .browserVersion(getBrowserVersion(ua))
                .os(getOperatingSystem(ua))
                .osVersion(getOSVersion(ua))
                .deviceType(getDeviceType(ua))
                .deviceBrand(getDeviceBrand(ua))
                .deviceModel(getDeviceModel(ua))
                .isMobile(isMobile(ua))
                .isTablet(isTablet(ua))
                .layoutEngine(getLayoutEngine(ua))
                .layoutEngineVersion(getLayoutEngineVersion(ua))
                .requestMethod(getRequestMethod(request))
                .requestUrl(getRequestUrl(request))
                .referer(getReferer(request))
                .origin(getOrigin(request))
                .sessionId(getSessionId(request))
                .fingerprint(generateFingerprint(request))
                .acceptLanguage(getAcceptLanguage(request))
                .appVersion(getAppVersion(request))
                .appPlatform(getAppPlatform(request))
                .appBuild(getAppBuild(request))
                .appChannel(getAppChannel(request))
                .agentClass(getAgentClass(ua))
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ==================== UA 解析（私有核心方法） ====================

    /**
     * 解析 UserAgent，返回 {@link UserAgent} 实例。
     * <p>
     * 此为私有方法，外部通过 {@link #getClientInfo} 间接使用。
     * Yauaa 内置 LRU 缓存，相同 UA 字符串命中缓存时耗时 < 1μs。
     */
    private static UserAgent parseUserAgent(HttpServletRequest request) {
        if (!uaEnabled) {
            return null;
        }
        try {
            String uaStr = getUserAgentString(request);
            if (UNKNOWN.equals(uaStr)) {
                return null;
            }
            return AnalyzerHolder.INSTANCE.parse(uaStr);
        } catch (Exception e) {
            log.warn("[ClientInfoUtil] 解析 UserAgent 失败", e);
            return null;
        }
    }

    /**
     * 从 {@link UserAgent} 中安全提取字段值。
     * 若 ua 为 null、字段为空或等于 "??" 等无效值，统一返回 {@code UNKNOWN}。
     */
    private static String extractField(UserAgent ua, String fieldName) {
        if (ua == null) {
            return UNKNOWN;
        }
        String value = ua.getValue(fieldName);
        return StringUtils.hasText(value) && !UNKNOWN.equals(value) ? value : UNKNOWN;
    }

    // ==================== UA 字段提取（接受已解析实例，无重复解析） ====================

    public static String getBrowser(UserAgent ua) {
        return extractField(ua, "AgentName");
    }

    public static String getBrowserVersion(UserAgent ua) {
        return extractField(ua, "AgentVersion");
    }

    public static String getOperatingSystem(UserAgent ua) {
        return extractField(ua, "OperatingSystemName");
    }

    public static String getOSVersion(UserAgent ua) {
        return extractField(ua, "OperatingSystemVersion");
    }

    public static String getDeviceType(UserAgent ua) {
        return extractField(ua, "DeviceClass");
    }

    public static String getDeviceBrand(UserAgent ua) {
        return extractField(ua, "DeviceBrand");
    }

    public static String getDeviceModel(UserAgent ua) {
        return extractField(ua, "DeviceName");
    }

    public static Boolean isMobile(UserAgent ua) {
        if (ua == null) {
            return null;
        }
        return "Phone".equalsIgnoreCase(extractField(ua, "DeviceClass"));
    }

    public static Boolean isTablet(UserAgent ua) {
        if (ua == null) {
            return null;
        }
        return "Tablet".equalsIgnoreCase(extractField(ua, "DeviceClass"));
    }

    public static String getLayoutEngine(UserAgent ua) {
        return extractField(ua, "LayoutEngineName");
    }

    public static String getLayoutEngineVersion(UserAgent ua) {
        return extractField(ua, "LayoutEngineVersion");
    }

    public static String getAgentClass(UserAgent ua) {
        return extractField(ua, "AgentClass");
    }

    // ==================== App 版本获取方法 ====================

    /**
     * 获取 App 版本号，多策略按优先级降级：
     * <ol>
     *   <li>{@code App-Version} Header</li>
     *   <li>{@code X-App-Version} Header</li>
     *   <li>{@code Client-Version} Header</li>
     *   <li>请求参数 {@code appVersion}</li>
     *   <li>从 User-Agent 字符串中解析</li>
     * </ol>
     */
    public static String getAppVersion(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String version = firstNonBlank(
                request.getHeader(HEADER_APP_VERSION),
                request.getHeader(HEADER_X_APP_VERSION),
                request.getHeader(HEADER_CLIENT_VERSION),
                request.getParameter("appVersion")
        );
        if (version != null) {
            return version;
        }
        String fromUA = parseAppVersionFromUA(getUserAgentString(request));
        return StringUtils.hasText(fromUA) ? fromUA : UNKNOWN;
    }

    /**
     * 从 User-Agent 字符串中解析 App 版本。
     * 过滤掉标准浏览器/系统字段，只匹配业务 App 自己注入的版本段。
     */
    public static String parseAppVersionFromUA(String uaString) {
        if (!StringUtils.hasText(uaString) || UNKNOWN.equals(uaString)) {
            return null;
        }
        if (uaString.contains(UA_MINIPROGRAM_FLAG)) {
            Matcher mpMatcher = UA_MINIPROGRAM_VERSION_PATTERN.matcher(uaString);
            if (mpMatcher.find()) {
                return mpMatcher.group(1);
            }
        }
        Set<String> blacklist = Set.of(
                "Mozilla", "AppleWebKit", "Chrome", "Safari", "Firefox",
                "Edge", "OPR", "Version", "Mobile", "MicroMessenger",
                "WeChat", "Gecko", "Trident", "MSIE", "Opera",
                "Linux", "Windows", "Macintosh", "like"
        );
        Matcher matcher = UA_APP_VERSION_PATTERN.matcher(uaString);
        while (matcher.find()) {
            String appName = matcher.group(1).replace("/", "");
            String appVersion = matcher.group(2);
            if (!blacklist.contains(appName)) {
                log.debug("[ClientInfoUtil] 从 UA 提取 App 版本: {}={}", appName, appVersion);
                return appVersion;
            }
        }
        return null;
    }

    /**
     * 获取 App 平台，优先级：Header → 请求参数 → UA 推断。
     */
    public static String getAppPlatform(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String platform = firstNonBlank(
                request.getHeader(HEADER_APP_PLATFORM),
                request.getParameter("platform")
        );
        if (platform != null) {
            return platform.toLowerCase();
        }
        return inferPlatformFromUA(getUserAgentString(request));
    }

    /**
     * 根据 User-Agent 关键词推断平台
     *
     * @param uaString User-Agent 原始字符串
     * @return 推断出的平台标识
     */
    private static String inferPlatformFromUA(String uaString) {
        if (!StringUtils.hasText(uaString) || UNKNOWN.equals(uaString)) {
            return UNKNOWN;
        }
        String ua = uaString.toLowerCase();

        // 微信小程序（需在 Android/iOS 判断前检测）
        if (ua.contains("miniprogram")) {
            return "miniprogram";
        }
        // 鸿蒙
        if (ua.contains("harmony") || ua.contains("harmonyos")) {
            return "harmonyos";
        }
        // Android
        if (ua.contains("android")) {
            return "android";
        }
        // iOS（iPhone/iPad/iPod）
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            return "ios";
        }
        // Windows / Mac / Linux 归为 web
        if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux")) {
            return "web";
        }
        return UNKNOWN;
    }

    /**
     * 获取 App 构建号，来源：{@code App-Build} Header 或请求参数 {@code appBuild}。
     */
    public static String getAppBuild(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String build = firstNonBlank(
                request.getHeader(HEADER_APP_BUILD),
                request.getParameter("appBuild")
        );
        return build != null ? build : UNKNOWN;
    }

    /**
     * 获取 App 渠道，来源：{@code App-Channel} Header 或请求参数 {@code channel}。
     */
    public static String getAppChannel(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String channel = firstNonBlank(
                request.getHeader(HEADER_APP_CHANNEL),
                request.getParameter("channel")
        );
        return channel != null ? channel : UNKNOWN;
    }

    // ==================== 请求信息提取 ====================

    /**
     * 获取User-Agent字符串
     */
    public static String getUserAgentString(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String ua = request.getHeader("User-Agent");
        return StringUtils.hasText(ua) ? ua : UNKNOWN;
    }

    public static String getRequestMethod(HttpServletRequest request) {
        return request != null ? request.getMethod() : UNKNOWN;
    }

    /**
     * 获取请求URL
     */
    public static String getRequestUrl(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        StringBuffer url = request.getRequestURL();
        String queryString = request.getQueryString();
        if (StringUtils.hasText(queryString)) {
            url.append(QUESTION_MARK).append(queryString);
        }
        return url.toString();
    }

    /**
     * 获取来源页面
     */
    public static String getReferer(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String referer = request.getHeader("Referer");
        return StringUtils.hasText(referer) ? referer : UNKNOWN;
    }

    /**
     * 获取请求来源
     */
    public static String getOrigin(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String origin = request.getHeader("Origin");
        return StringUtils.hasText(origin) ? origin : UNKNOWN;
    }

    /**
     * 获取 SessionId
     */
    public static String getSessionId(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        HttpSession session = request.getSession(false);
        return session != null ? session.getId() : UNKNOWN;
    }

    /**
     * 获取接受的语言
     */
    public static String getAcceptLanguage(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String language = request.getHeader("Accept-Language");
        return StringUtils.hasText(language) ? language : UNKNOWN;
    }

    /**
     * 生成客户端指纹
     */
    public static String generateFingerprint(HttpServletRequest request) {
        String raw = getClientIp(request) + VERTICAL_BAR
                + getUserAgentString(request) + VERTICAL_BAR
                + getAcceptLanguage(request);
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取所有请求头
     */
    public static Map<String, String> getAllHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        if (request == null) {
            return headers;
        }
        var headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    // ==================== 工具方法 ====================

    /**
     * 返回第一个非空白（trim 后有内容）的字符串，全为空则返回 null。
     */
    private static String firstNonBlank(String... candidates) {
        for (String s : candidates) {
            if (StringUtils.hasText(s)) {
                return s.trim();
            }
        }
        return null;
    }

    // ==================== 数据类 ====================

    @Data
    @Builder
    public static class ClientInfo {
        private String ip;
        private String userAgent;
        private String browser;
        private String browserVersion;
        private String os;
        private String osVersion;
        private String deviceType;
        private String deviceBrand;
        private String deviceModel;
        private Boolean isMobile;
        private Boolean isTablet;
        private String layoutEngine;
        private String layoutEngineVersion;
        private String requestMethod;
        private String requestUrl;
        private String referer;
        private String origin;
        private String sessionId;
        private String fingerprint;
        private String acceptLanguage;
        private String appVersion;
        private String appPlatform;
        private String appBuild;
        private String appChannel;
        private String agentClass;
        private Long timestamp;
    }
}