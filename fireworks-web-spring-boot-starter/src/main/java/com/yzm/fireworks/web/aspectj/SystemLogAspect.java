package com.yzm.fireworks.web.aspectj;

import com.google.common.base.Stopwatch;
import com.yzm.fireworks.common.annotation.Sensitive;
import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.common.util.ObjectMapperUtil;
import com.yzm.fireworks.common.util.StrUtil;
import com.yzm.fireworks.web.properties.SystemLogProperties;
import com.yzm.fireworks.web.util.JoinPointUtil;
import com.yzm.fireworks.web.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.yzm.fireworks.common.constants.StringPool.*;


/**
 * 请求日志切面
 *
 * <p>拦截所有标注了 {@link Controller} 或 {@link RestController} 的类中的方法，
 * 记录请求和响应的日志信息。
 *
 * <p><b>关键设计点</b>：
 * <ul>
 *   <li>使用 Advisor 模式而非 @Aspect 注解模式，避免使用 ThreadLocal 存储状态</li>
 *   <li>所有逻辑在 {@link #invoke} 方法中完成，包括前置日志、方法执行、后置日志</li>
 *   <li>{@link #matches} 结果按 targetClass 缓存，避免每次请求重复反射扫描注解</li>
 *   <li>支持排除特定路径 of 日志记录</li>
 *   <li>支持参数脱敏，避免敏感信息泄露</li>
 *   <li>支持慢请求检测，超过阈值时升级为 WARN 日志</li>
 *   <li>支持日志内容截断，防止超大响应体撑爆日志系统</li>
 * </ul>
 *
 * @author JYuan
 */
@Slf4j
public class SystemLogAspect extends StaticMethodMatcherPointcutAdvisor implements MethodInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * {@link #matches} 结果缓存，key 为 targetClass，避免重复反射
     */
    private final Map<Class<?>, Boolean> matchCache = new ConcurrentHashMap<>();

    private final SystemLogProperties properties;

    public SystemLogAspect(SystemLogProperties properties) {
        this.properties = properties;
        this.setAdvice(this);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        // 检查类上是否有 @Controller 或 @RestController 注解，结果按 targetClass 缓存
        return matchCache.computeIfAbsent(targetClass, clazz ->
                AnnotatedElementUtils.hasAnnotation(clazz, Controller.class)
                        || AnnotatedElementUtils.hasAnnotation(clazz, RestController.class));
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Object[] arguments = invocation.getArguments();

        // ── 检查是否需要记录日志 ───────────────────────────────────────
        HttpServletRequest request = ServletUtil.getRequest();
        if (request == null) {
            // 非 Web 环境，直接执行方法
            return invocation.proceed();
        }

        String uri = request.getRequestURI();

        // 在排除路径中，不记录日志，直接执行方法
        if (isExcluded(uri)) {
            return invocation.proceed();
        }

        // ── 记录请求开始日志 ───────────────────────────────────────────
        String requestMethod = request.getMethod();
        String url = request.getRequestURL().toString();

        String headers = processHeaders(request);
        String params = EMPTY;
        if (properties.isPrintParams()) {
            String[] paramNames = JoinPointUtil.getParamNames(method);
            Annotation[][] paramAnnotations = method.getParameterAnnotations();
            params = processParam(paramNames, arguments, paramAnnotations);
        }

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

        // ── 执行目标方法 ──────────────────────────────────────────────
        Object result = null;
        Throwable ex = null;
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            result = invocation.proceed();
            return result;
        } catch (Throwable t) {
            ex = t;
            throw t;
        } finally {
            stopwatch.stop();
            long costMs = stopwatch.elapsed(TimeUnit.MILLISECONDS);
            String costTime = stopwatch.toString();

            // 获取 HTTP 状态码
            HttpServletResponse response = ServletUtil.getResponse();
            int status = response != null ? response.getStatus() : -1;

            // ── 判断是否慢请求 ─────────────────────────────────────────
            long slowThresholdMs = properties.getSlowThresholdMs();
            boolean isSlow = slowThresholdMs > 0 && costMs >= slowThresholdMs;

            // ── 记录请求结束日志 ───────────────────────────────────────
            if (ex != null) {
                // 异常情况：固定使用 WARN
                log.warn("RequestEnd {} {} status={} <<<<<< cost={} exception={}",
                        requestMethod, url, status, costTime, ex.getMessage());
            } else if (properties.isPrintResult()) {
                // 正常情况，打印结果
                String resultStr = StrUtil.truncate(ObjectMapperUtil.stringify(result), properties.getMaxLength());
                if (isSlow) {
                    log.warn("RequestEnd {} {} status={} <<<<<< cost={} [SLOW] result={}",
                            requestMethod, url, status, costTime, resultStr);
                } else {
                    log.info("RequestEnd {} {} status={} <<<<<< cost={} result={}",
                            requestMethod, url, status, costTime, resultStr);
                }
            } else {
                // 正常情况，不打印结果
                if (isSlow) {
                    log.warn("RequestEnd {} {} status={} <<<<<< cost={} [SLOW]",
                            requestMethod, url, status, costTime);
                } else {
                    log.info("RequestEnd {} {} status={} <<<<<< cost={}",
                            requestMethod, url, status, costTime);
                }
            }
        }
    }

    /**
     * 按白名单提取请求 Header 并格式化为日志字符串。
     * 使用 {@link StringJoiner} 拼接，避免手动处理末尾逗号。
     *
     * @param request 当前请求
     * @return 格式化后的 Header 字符串，如 {@code headers=Authorization:[Bearer xxx],X-User-Id:[123]}；
     * 白名单为空时返回 {@code null}
     */
    private String processHeaders(HttpServletRequest request) {
        List<String> includeHeaders = properties.getIncludeHeaders();
        if (CollectionUtils.isEmpty(includeHeaders)) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(COMMA, "headers=", EMPTY);
        for (String header : includeHeaders) {
            String trimmed = header.trim();
            if (StringUtils.hasText(trimmed)) {
                joiner.add(trimmed + ":[" + request.getHeader(trimmed) + RIGHT_SQUARE_BRACKET);
            }
        }
        // 若所有 header 名均为空白，joiner 仅含前缀，视为无效
        String result = joiner.toString();
        return "headers=".equals(result) ? null : result;
    }

    /**
     * 收集方法入参并序列化为日志字符串。
     * 若参数上标注了 {@link Sensitive}，则在打印前先对参数值执行脱敏处理，
     * 避免手机号、身份证等敏感信息明文出现在日志中。
     *
     * @param paramNames       参数名数组
     * @param paramValues      参数值数组
     * @param paramAnnotations 参数注解二维数组
     * @return 序列化后的参数字符串
     */
    private String processParam(String[] paramNames, Object[] paramValues, Annotation[][] paramAnnotations) {
        // 使用 JoinPointUtil 的统一处理方法，包括 MultipartFile、InputStream、Reader 等特殊类型
        Map<String, Object> paramMap = JoinPointUtil.processParamsToMap(paramNames, paramValues, paramAnnotations);
        if (paramMap.isEmpty()) {
            return EMPTY;
        }

        try {
            return "params=" + StrUtil.truncate(ObjectMapperUtil.stringify(paramMap), properties.getMaxLength());
        } catch (Exception e) {
            return "params=[serialize error]";
        }
    }

    private boolean isExcluded(String uri) {
        List<String> excludePaths = properties.getExcludePaths();
        if (ObjectUtils.isEmpty(excludePaths)) {
            return false;
        }
        return excludePaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }
}