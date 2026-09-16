package com.yzm.fireworks.common.expression;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;

/**
 * 方法级 SpEL 求值上下文
 * <p>
 * 在 Spring {@link MethodBasedEvaluationContext} 基础上，统一约定两个框架保留变量：
 * <ul>
 *     <li>{@code #_result}：被拦截方法的返回值</li>
 *     <li>{@code #_exception}：被拦截方法抛出的异常对象，可取
 *         {@code #_exception.message}、{@code #_exception.class.name}</li>
 * </ul>
 * <p>
 * <b>为什么带下划线前缀</b>：变量名需与业务方法参数名共存于同一命名空间。
 * 业务参数名会覆盖同名的框架变量，因此框架变量统一使用 {@code _} 前缀降低撞名概率，
 * 同时明确区分「框架保留」与「业务自定义」。
 * <p>
 * <b>关于方法参数</b>：由父类原生提供 {@code #参数名}、{@code #a0}、{@code #p0}
 * 三种访问方式，不额外注入 {@code #args} 数组，避免与业务参数名冲突。
 *
 * @author JYuan
 */
public class SpelEvaluationContext extends MethodBasedEvaluationContext {

    /**
     * 方法返回值变量名
     */
    public static final String RESULT_VARIABLE = "_result";

    /**
     * 方法异常变量名
     */
    public static final String EXCEPTION_VARIABLE = "_exception";

    public SpelEvaluationContext(@Nullable Method method, Object[] args,
                                 ParameterNameDiscoverer parameterNameDiscoverer) {
        this(null, method, args, parameterNameDiscoverer, null, null);
    }

    /**
     * @param rootObject              根对象，可为 {@code null}
     * @param method                  被拦截的方法
     * @param args                    方法参数
     * @param parameterNameDiscoverer 参数名发现器
     * @param result                  方法返回值，可为 {@code null}
     * @param exception               方法异常，可为 {@code null}
     */
    public SpelEvaluationContext(@Nullable Object rootObject, @Nullable Method method, Object[] args,
                                 ParameterNameDiscoverer parameterNameDiscoverer,
                                 @Nullable Object result, @Nullable Throwable exception) {
        super(rootObject, method, args, parameterNameDiscoverer);
        setVariable(RESULT_VARIABLE, result);
        setVariable(EXCEPTION_VARIABLE, exception);
    }
}
