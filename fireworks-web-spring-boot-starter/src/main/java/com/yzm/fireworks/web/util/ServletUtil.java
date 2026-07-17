package com.yzm.fireworks.web.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servlet 上下文辅助工具类
 *
 * @author JYuan
 */
@Slf4j
public class ServletUtil {

    private ServletUtil() {
        // 私有构造
    }

    /**
     * 获取 request
     *
     * <p><b>注意：</b>异步线程（{@code @Async}、线程池）中 {@code RequestContextHolder}
     * 无法传递上下文，调用此方法会返回 {@code null}，请提前在同步阶段取出所需信息。
     *
     * @return 当前请求，不在 Web 请求线程中时返回 {@code null}
     */
    @Nullable
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 获取 response
     *
     * @return 当前响应，不在 Web 请求线程中时返回 {@code null}
     */
    @Nullable
    public static HttpServletResponse getResponse() {
        ServletRequestAttributes attrs = getRequestAttributes();
        return attrs != null ? attrs.getResponse() : null;
    }

    /**
     * 获取 session（不存在时不自动创建）
     *
     * @return 当前 session，不在 Web 请求线程中或 session 不存在时返回 {@code null}
     */
    @Nullable
    public static HttpSession getSession() {
        HttpServletRequest request = getRequest();
        return request != null ? request.getSession(false) : null;
    }

    /**
     * 获取 {@link ServletRequestAttributes}
     *
     * <p><b>注意：</b>{@code RequestContextHolder} 在异步方法中可能无法获取到上下文，
     * 就算获取到，请求对象的内容也可能已被清空，请勿在 {@code @Async} 方法中使用。
     *
     * @return {@link ServletRequestAttributes}，不在 Web 请求线程中时返回 {@code null}
     */
    @Nullable
    public static ServletRequestAttributes getRequestAttributes() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return (ServletRequestAttributes) attributes;
    }
}