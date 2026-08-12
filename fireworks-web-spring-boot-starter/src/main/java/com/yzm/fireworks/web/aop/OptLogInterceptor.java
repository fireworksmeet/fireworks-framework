package com.yzm.fireworks.web.aop;

import com.google.common.base.Stopwatch;
import com.yzm.fireworks.api.annotation.OptLog;
import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.common.util.JsonUtil;
import com.yzm.fireworks.web.context.OptLogContext;
import com.yzm.fireworks.web.model.OptLogOperator;
import com.yzm.fireworks.web.service.OptLogService;
import com.yzm.fireworks.web.spi.OptLogOperatorProvider;
import com.yzm.fireworks.web.util.ClientInfoUtil;
import com.yzm.fireworks.web.util.JoinPointUtil;
import com.yzm.fireworks.web.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作日志拦截器
 *
 * <p>拦截所有标注了 {@link OptLog} 的方法，在方法执行完毕后同步构建 {@link OptLogContext}，
 * 再交给 {@link OptLogService#handleLog} 处理（通常为异步落库）。
 *
 * <p><b>关键设计点</b>：
 * <ul>
 *   <li>IP、UA 等请求信息必须在切面中同步采集，{@code HttpServletRequest}
 *       在异步线程中不可用</li>
 *   <li>操作人信息通过 {@link OptLogOperatorProvider} SPI 获取，框架层不依赖
 *       任何具体认证框架（Spring Security / Sa-Token / 自定义 ThreadLocal 均可）</li>
 *   <li>{@link OptLogService} 和 {@link OptLogOperatorProvider} 均通过
 *       {@link com.yzm.fireworks.common.util.SpringContextHolder#getBean} 懒加载，未注册时静默跳过</li>
 * </ul>
 *
 * @author JYuan
 */
@Slf4j
public class OptLogInterceptor implements MethodInterceptor {

    private final OptLogMetadataSource metadataSource;
    private final OptLogOperatorProvider operatorProvider;
    private final OptLogService optLogService;

    public OptLogInterceptor(
            OptLogMetadataSource metadataSource,
            @Nullable @Lazy OptLogOperatorProvider operatorProvider,
            @Nullable @Lazy OptLogService optLogService) {
        this.metadataSource = metadataSource;
        this.operatorProvider = operatorProvider;
        this.optLogService = optLogService;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Class<?> targetClass = invocation.getThis() != null ? invocation.getThis().getClass() : null;

        OptLogAttribute attribute = metadataSource.getMetadata(method, targetClass);
        if (attribute == null || ObjectUtils.isEmpty(optLogService)) {
            return invocation.proceed();
        }

        // ── 在方法执行前同步采集请求上下文 ──────────────────────────────────
        String ip = null, userAgent = null, browser = null, os = null,
                requestUri = null, requestMethod = null;

        try {
            HttpServletRequest request = ServletUtil.getRequest();
            if (request != null) {
                ClientInfoUtil.ClientInfo clientInfo = ClientInfoUtil.getClientInfo(request);
                ip = clientInfo.getIp();
                userAgent = clientInfo.getUserAgent();
                browser = clientInfo.getBrowser()
                        + (StringUtils.hasText(clientInfo.getBrowserVersion()) ? StringPool.SPACE + clientInfo.getBrowserVersion() : StringPool.EMPTY);
                os = clientInfo.getOs()
                        + (StringUtils.hasText(clientInfo.getOsVersion()) ? StringPool.SPACE + clientInfo.getOsVersion() : StringPool.EMPTY);
                requestUri = request.getRequestURI();
                requestMethod = request.getMethod();
            }
        } catch (Exception e) {
            log.debug("[OptLogInterceptor] 获取请求信息失败: {}", e.getMessage());
        }

        // ── 通过 SPI 获取操作人（与认证框架解耦） ────────────────────────────
        // 业务层注册 OptLogOperatorProvider Bean 即可；未注册时操作人字段留空
        Long operatorId = null;
        String operatorName = null;

        try {
            if (operatorProvider != null) {
                OptLogOperator operator = operatorProvider.currentOperator();
                if (operator != null) {
                    operatorId = operator.getOperatorId();
                    operatorName = operator.getOperatorName();
                }
            }
        } catch (Exception e) {
            log.debug("[OptLogInterceptor] 获取操作人信息失败: {}", e.getMessage());
        }

        // ── 执行目标方法 ──────────────────────────────────────────────────────
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
            String costTime = stopwatch.stop().toString();
            buildAndHandleLog(invocation, attribute, result, ex, costTime,
                    ip, userAgent, browser, os, requestUri, requestMethod,
                    operatorId, operatorName);
        }
    }

    private void buildAndHandleLog(MethodInvocation invocation, OptLogAttribute attribute, Object result, Throwable ex,
                                   String costTime, String ip, String userAgent, String browser,
                                   String os, String requestUri, String requestMethod,
                                   Long operatorId, String operatorName) {
        if (ObjectUtils.isEmpty(optLogService)) {
            return;
        }

        try {

            Method method = invocation.getMethod();
            Class<?> targetClass = invocation.getThis() != null ? invocation.getThis().getClass() : null;
            Object[] arguments = invocation.getArguments();

            OptLogContext.OptLogContextBuilder builder = OptLogContext.builder()
                    .module(attribute.getModule())
                    .type(attribute.getType())
                    .description(attribute.getDescription())
                    .className(targetClass != null ? targetClass.getName() : null)
                    .methodName(method.getName())
                    .success(ex == null)
                    .costTime(costTime)
                    .operateTime(LocalDateTime.now())
                    .operatorId(operatorId)
                    .operatorName(operatorName)
                    .ip(ip)
                    .userAgent(userAgent)
                    .browser(browser)
                    .os(os)
                    .requestUri(requestUri)
                    .requestMethod(requestMethod)
                    .method(method)
                    .methodArgs(arguments)
                    .result(result)
                    .throwable(ex);

            if (attribute.isRecordArgs() && !ObjectUtils.isEmpty(arguments)) {
                // 获取方法参数注解，进行脱敏处理
                Annotation[][] paramAnnotations = method.getParameterAnnotations();
                Object[] processedArgs = JoinPointUtil.processParams(arguments, paramAnnotations);
                builder.argsJson(safeToJson(processedArgs));
            }
            if (attribute.isRecordResult() && !ObjectUtils.isEmpty(result)) {
                builder.resultJson(safeToJson(result));
            }
            if (ex != null) {
                String msg = ex.getMessage();
                builder.errorType(ex.getClass().getName())
                        .errorMsg(msg != null && msg.length() > 500 ? msg.substring(0, 500) : msg);
            }

            optLogService.handleLog(builder.build());
        } catch (Exception e) {
            log.warn("[OptLogInterceptor] 构建操作日志失败", e);
        }
    }

    private String safeToJson(Object obj) {
        try {
            return JsonUtil.stringify(obj);
        } catch (Exception e) {
            return "[序列化失败: " + e.getMessage() + "]";
        }
    }
}