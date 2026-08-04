package com.yzm.fireworks.web.filter;

import com.google.common.base.Stopwatch;
import com.yzm.fireworks.common.util.JsonUtil;
import com.yzm.fireworks.common.util.StrUtil;
import com.yzm.fireworks.web.config.properties.SystemLogProperties;
import com.yzm.fireworks.web.handler.RepeatableReadRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.yzm.fireworks.common.constants.StringPool.*;

/**
 * 生产级 HTTP 全局日志 Filter
 *
 * <p>一站式完成：路径白名单过滤、Content-Type 识别、条件包装、请求/响应日志打印及慢请求预警。
 */
@Slf4j
public class SystemLogFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private final SystemLogProperties properties;

    public SystemLogFilter(SystemLogProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // 1. 白名单路径校验
        if (isExcluded(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 判断 Content-Type，仅对 JSON/Form/Text 等文本数据进行安全包装
        boolean shouldWrap = isLoggableContentType(request.getContentType());
        HttpServletRequest requestToUse = request;
        RepeatableReadRequestWrapper wrappedRequest = null;

        if (shouldWrap && !(request instanceof RepeatableReadRequestWrapper)) {
            wrappedRequest = new RepeatableReadRequestWrapper(request);
            requestToUse = wrappedRequest;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        Throwable exception = null;

        try {
            // 3. 打印请求进入日志
            printRequestStartLog(requestToUse, wrappedRequest);

            // 4. 执行后续责任链
            filterChain.doFilter(requestToUse, response);

        } catch (Throwable t) {
            exception = t;
            throw t;
        } finally {
            stopwatch.stop();
            long costMs = stopwatch.elapsed(TimeUnit.MILLISECONDS);

            // 5. 打印请求结束日志（包含 HTTP Status 与耗时）
            printRequestEndLog(requestToUse, response, stopwatch.toString(), costMs, exception);
        }
    }

    private void printRequestStartLog(HttpServletRequest request, RepeatableReadRequestWrapper wrappedRequest) {
        String requestMethod = request.getMethod();
        String url = request.getRequestURL().toString();

        String headers = processHeaders(request);
        String params = properties.isPrintParams() ? processParams(request, wrappedRequest) : EMPTY;

        StringJoiner logJoiner = new StringJoiner(SPACE);
        logJoiner.add("RequestStart").add(requestMethod).add(url);
        if (StringUtils.hasText(headers)) {
            logJoiner.add(headers);
        }
        if (StringUtils.hasText(params)) {
            logJoiner.add(params);
        }
        logJoiner.add(">>>>>>");
        log.info(logJoiner.toString());
    }

    private void printRequestEndLog(HttpServletRequest request, HttpServletResponse response,
                                    String costTime, long costMs, Throwable ex) {
        String requestMethod = request.getMethod();
        String url = request.getRequestURL().toString();
        int status = response.getStatus();

        long slowThresholdMs = properties.getSlowThresholdMs();
        boolean isSlow = slowThresholdMs > 0 && costMs >= slowThresholdMs;

        if (ex != null) {
            log.warn("RequestEnd {} {} status={} <<<<<< cost={} exception={}",
                    requestMethod, url, status, costTime, ex.getMessage());
        } else if (isSlow) {
            log.warn("RequestEnd {} {} status={} <<<<<< cost={} [SLOW]",
                    requestMethod, url, status, costTime);
        } else {
            log.info("RequestEnd {} {} status={} <<<<<< cost={}",
                    requestMethod, url, status, costTime);
        }
    }

    private String processParams(HttpServletRequest request, RepeatableReadRequestWrapper wrappedRequest) {
        Map<String, Object> paramMap = new LinkedHashMap<>();

        // 提取 URL Query 参数
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (!parameterMap.isEmpty()) {
            parameterMap.forEach((k, v) -> {
                if (v != null && v.length > 0) {
                    paramMap.put(k, v.length == 1 ? v[0] : Arrays.asList(v));
                }
            });
        }

        // 提取 Body 参数
        if (wrappedRequest != null) {
            byte[] bodyBytes = wrappedRequest.getBody();
            if (bodyBytes != null && bodyBytes.length > 0) {
                String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
                if (wrappedRequest.isTruncated()) {
                    bodyStr += " [TRUNCATED_MAX_LIMIT]";
                }
                try {
                    Map<String, Object> bodyMap = JsonUtil.toMap(bodyStr);
                    paramMap.put("requestBody", bodyMap != null ? bodyMap : bodyStr);
                } catch (Exception e) {
                    paramMap.put("requestBody", bodyStr);
                }
            }
        }

        if (paramMap.isEmpty()) {
            return EMPTY;
        }

        try {
            return "params=" + StrUtil.truncate(JsonUtil.stringify(paramMap), properties.getMaxLength());
        } catch (Exception e) {
            return "params=[serialize error]";
        }
    }

    private String processHeaders(HttpServletRequest request) {
        List<String> includeHeaders = properties.getIncludeHeaders();
        if (CollectionUtils.isEmpty(includeHeaders)) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(COMMA, "headers=", EMPTY);
        for (String header : includeHeaders) {
            String trimmed = header.trim();
            if (StringUtils.hasText(trimmed)) {
                joiner.add(trimmed + ":[" + request.getHeader(trimmed) + "]");
            }
        }
        String result = joiner.toString();
        return "headers=".equals(result) ? null : result;
    }

    private boolean isLoggableContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        // 绝不读取大文件上传与二进制流
        if (contentType.contains(MediaType.MULTIPART_FORM_DATA_VALUE) ||
            contentType.contains(MediaType.APPLICATION_OCTET_STREAM_VALUE) ||
            contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return false;
        }
        return contentType.contains(MediaType.APPLICATION_JSON_VALUE) ||
               contentType.contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE) ||
               contentType.contains(MediaType.APPLICATION_XML_VALUE) ||
               contentType.contains(MediaType.TEXT_PLAIN_VALUE);
    }

    private boolean isExcluded(String uri) {
        List<String> excludePaths = properties.getExcludePaths();
        if (ObjectUtils.isEmpty(excludePaths)) {
            return false;
        }
        return excludePaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }
}