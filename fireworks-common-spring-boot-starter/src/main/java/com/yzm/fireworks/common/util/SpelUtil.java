package com.yzm.fireworks.common.util;

import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;

import java.lang.reflect.Method;

/**
 * SpEL 工具类。
 *
 * <p>
 * 提供 SpEL 表达式解析、StandardEvaluationContext 创建、变量绑定以及
 * 表达式执行等基础能力。
 *
 * <p>
 * 本工具类不与具体业务场景绑定，可以用于：
 *
 * <ul>
 *     <li>普通业务代码</li>
 *     <li>Spring AOP</li>
 *     <li>Advisor + MetadataSource</li>
 *     <li>注解驱动框架</li>
 *     <li>规则引擎</li>
 *     <li>动态条件判断</li>
 * </ul>
 *
 * <p>
 * 方法参数场景支持：
 *
 * <pre>
 * #userId
 * #user
 * #p0
 * #p1
 * #a0
 * #a1
 * #args[0]
 * #args[1]
 * </pre>
 *
 * <p>
 * 方法执行结果支持：
 *
 * <pre>
 * #result
 * #result.id
 * </pre>
 *
 * <p>
 * 方法异常支持：
 *
 * <pre>
 * #exception
 * #exception.message
 * </pre>
 *
 * <p>
 * Spring Bean 调用：
 * </pre>
 *
 * <p>
 * 本工具类不负责 Expression 缓存。
 * 如果上层存在 Metadata / Attribute 缓存，应由上层负责缓存
 * 已解析的 {@link Expression}。
 *
 * @author JYuan
 */
@UtilityClass
public class SpelUtil {

    /**
     * SpEL 表达式解析器。
     *
     * <p>
     * SpelExpressionParser 本身无状态，可以安全复用。
     */
    private static final ExpressionParser PARSER =
            new SpelExpressionParser();

    /**
     * 方法参数名称发现器。
     *
     * <p>
     * 用于 MethodBasedEvaluationContext 解析：
     *
     * <pre>
     * #userId
     * #user
     * </pre>
     */
    private static final ParameterNameDiscoverer
            PARAMETER_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();

    /**
     * 方法参数数组变量。
     *
     * <p>
     * 例如：
     *
     * <pre>
     * #args[0]
     * #args[1]
     * </pre>
     */
    public static final String ARGS_VARIABLE = "args";

    /**
     * 方法返回值变量。
     *
     * <p>
     * 例如：
     *
     * <pre>
     * #result
     * #result.id
     * </pre>
     */
    public static final String RESULT_VARIABLE = "result";

    /**
     * 方法异常变量。
     *
     * <p>
     * 例如：
     *
     * <pre>
     * #exception
     * #exception.message
     * </pre>
     */
    public static final String EXCEPTION_VARIABLE = "exception";

    // -------------------------------------------------------------------------
    // Expression
    // -------------------------------------------------------------------------

    /**
     * 解析 SpEL 表达式。
     *
     * <p>
     * 该方法只负责：
     *
     * <pre>
     * String -> Expression
     * </pre>
     *
     * <p>
     * 不负责缓存。
     *
     * @param expression SpEL 表达式
     * @return 已解析的 Expression
     */
    public static Expression parse(String expression) {
        Assert.hasText(expression, "SpEL expression must not be blank");

        return PARSER.parseExpression(expression);
    }

    // -------------------------------------------------------------------------
    // Standard Context
    // -------------------------------------------------------------------------

    /**
     * 创建一个标准 SpEL StandardEvaluationContext。
     *
     * <p>
     * 适用于不依赖 Java Method 的普通 SpEL 场景。
     *
     * <p>
     * 例如：
     *
     * <pre>
     * StandardEvaluationContext context =
     *         SpelUtils.createContext();
     *
     * SpelUtils.setVariable(
     *         context,
     *         "userId",
     *         100L
     * );
     *
     * Boolean result = SpelUtils.evaluate(
     *         "#userId != null",
     *         context,
     *         Boolean.class
     * );
     * </pre>
     *
     * @return StandardEvaluationContext
     */
    public static StandardEvaluationContext createContext() {
        return new StandardEvaluationContext();
    }

    /**
     * 创建一个以 rootObject 为根对象的 SpEL StandardEvaluationContext。
     *
     * <p>
     * 表达式可以直接访问 rootObject 的属性和方法。
     *
     * @param rootObject 根对象
     * @return StandardEvaluationContext
     */
    public static StandardEvaluationContext createContext(
            Object rootObject
    ) {
        return new StandardEvaluationContext(rootObject);
    }

    /**
     * 创建一个支持 Spring Bean 调用的标准 StandardEvaluationContext。
     *
     * @param beanFactory Spring BeanFactory
     * @return StandardEvaluationContext
     */
    public static StandardEvaluationContext createContext(
            BeanFactory beanFactory
    ) {
        StandardEvaluationContext context =
                new StandardEvaluationContext();

        setBeanFactory(
                context,
                beanFactory
        );

        return context;
    }

    /**
     * 创建一个以 rootObject 为根对象，并支持 Spring Bean 调用的
     * StandardEvaluationContext。
     *
     * @param rootObject  根对象
     * @param beanFactory Spring BeanFactory
     * @return StandardEvaluationContext
     */
    public static StandardEvaluationContext createContext(
            Object rootObject,
            BeanFactory beanFactory
    ) {
        StandardEvaluationContext context =
                new StandardEvaluationContext(rootObject);

        setBeanFactory(
                context,
                beanFactory
        );

        return context;
    }

    // -------------------------------------------------------------------------
    // Method Context
    // -------------------------------------------------------------------------

    /**
     * 创建方法级 MethodBasedEvaluationContext。
     *
     * <p>
     * 基于 Spring {@link MethodBasedEvaluationContext}。
     *
     * <p>
     * 支持：
     *
     * <pre>
     * #userId
     * #user
     * #p0
     * #p1
     * #a0
     * #a1
     * </pre>
     *
     * <p>
     * 同时额外提供：
     *
     * <pre>
     * #args[0]
     * #args[1]
     * </pre>
     *
     * @param target 当前目标对象，可为 null
     * @param method 当前方法
     * @param args   方法参数，可为 null
     * @return StandardEvaluationContext
     */
    public static MethodBasedEvaluationContext createMethodContext(
            Object target,
            Method method,
            Object[] args
    ) {
        return createMethodContext(
                target,
                method,
                args,
                null
        );
    }

    /**
     * 创建方法级 MethodBasedEvaluationContext，并支持 Spring Bean 调用。
     *
     * <p>
     * 支持：
     *
     * <pre>
     * #userId
     * #p0
     * #a0
     * #args[0]
     * </pre>
     *
     * <p>
     * 如果提供 BeanFactory，则支持：
     *
     * <pre>
     *
     * @param target      当前目标对象，可为 null
     * @param method      当前方法
     * @param args        方法参数，可为 null
     * @param beanFactory Spring BeanFactory，可为 null
     * @return StandardEvaluationContext
     */
    public static MethodBasedEvaluationContext createMethodContext(
            Object target,
            Method method,
            Object[] args,
            BeanFactory beanFactory
    ) {
        Assert.notNull(method, "Method must not be null");

        Object[] actualArgs = args == null
                ? new Object[0]
                : args;

        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(
                        target,
                        method,
                        actualArgs,
                        PARAMETER_NAME_DISCOVERER
                );

        /*
         * MethodBasedEvaluationContext 原生支持：
         *
         * #userId
         * #p0
         * #a0
         *
         * 这里额外提供：
         *
         * #args[0]
         */
        context.setVariable(
                ARGS_VARIABLE,
                actualArgs
        );

        if (beanFactory != null) {
            setBeanFactory(
                    context,
                    beanFactory
            );
        }

        return context;
    }

    // -------------------------------------------------------------------------
    // Variable
    // -------------------------------------------------------------------------

    /**
     * 设置 SpEL 变量。
     *
     * @param context StandardEvaluationContext
     * @param name    变量名
     * @param value   变量值
     */
    public static void setVariable(
            StandardEvaluationContext context,
            String name,
            Object value
    ) {
        requireContext(context);

        Assert.hasText(name, "Variable name must not be blank");

        context.setVariable(
                name,
                value
        );
    }

    /**
     * 设置方法返回值。
     *
     * <p>
     * 等价于：
     *
     * <pre>
     * setVariable(context, "result", result)
     * </pre>
     *
     * @param context StandardEvaluationContext
     * @param result  方法返回值
     */
    public static void setResult(
            StandardEvaluationContext context,
            Object result
    ) {
        setVariable(
                context,
                RESULT_VARIABLE,
                result
        );
    }

    /**
     * 设置方法异常。
     *
     * <p>
     * 等价于：
     *
     * <pre>
     * setVariable(context, "exception", exception)
     * </pre>
     *
     * @param context   StandardEvaluationContext
     * @param exception 方法异常
     */
    public static void setException(
            StandardEvaluationContext context,
            Throwable exception
    ) {
        setVariable(
                context,
                EXCEPTION_VARIABLE,
                exception
        );
    }

    // -------------------------------------------------------------------------
    // Bean
    // -------------------------------------------------------------------------

    /**
     * 为 StandardEvaluationContext 设置 Spring BeanFactory。
     *
     * <p>
     * 设置之后可以通过：
     *
     * <pre>
     *
     * 访问 Spring Bean。
     *
     * @param context    StandardEvaluationContext
     * @param beanFactory Spring BeanFactory
     */
    public static void setBeanFactory(
            StandardEvaluationContext context,
            BeanFactory beanFactory
    ) {
        requireContext(context);

        if (beanFactory == null) {
            return;
        }

        context.setBeanResolver(
                new BeanFactoryResolver(beanFactory)
        );
    }

    // -------------------------------------------------------------------------
    // Evaluate - Expression
    // -------------------------------------------------------------------------

    /**
     * 执行已经解析好的 Expression。
     *
     * @param expression Expression
     * @param context    StandardEvaluationContext
     * @return 表达式执行结果
     */
    public static Object evaluate(
            Expression expression,
            StandardEvaluationContext context
    ) {
        requireExpression(expression);
        requireContext(context);

        return expression.getValue(context);
    }

    /**
     * 执行已经解析好的 Expression，并指定返回类型。
     *
     * @param expression Expression
     * @param context    StandardEvaluationContext
     * @param resultType 返回类型
     * @param <T>        返回值类型
     * @return 表达式执行结果
     */
    public static <T> T evaluate(
            Expression expression,
            StandardEvaluationContext context,
            Class<T> resultType
    ) {
        requireExpression(expression);
        requireContext(context);

        Assert.notNull(resultType, "Result type must not be null");

        return expression.getValue(
                context,
                resultType
        );
    }

    // -------------------------------------------------------------------------
    // Evaluate - String
    // -------------------------------------------------------------------------

    /**
     * 解析并执行 SpEL 表达式。
     *
     * <p>
     * 适用于不需要自行管理 Expression 的简单场景。
     *
     * <p>
     * 如果表达式需要高频执行，建议调用方自行解析并缓存
     * {@link Expression}，然后使用 {@link #evaluate(Expression, StandardEvaluationContext)}。
     *
     * @param expression SpEL 表达式
     * @param context    StandardEvaluationContext
     * @return 表达式执行结果
     */
    public static Object evaluate(
            String expression,
            StandardEvaluationContext context
    ) {
        return evaluate(
                parse(expression),
                context
        );
    }

    /**
     * 解析并执行 SpEL 表达式，并指定返回类型。
     *
     * @param expression SpEL 表达式
     * @param context    StandardEvaluationContext
     * @param resultType 返回类型
     * @param <T>        返回值类型
     * @return 表达式执行结果
     */
    public static <T> T evaluate(
            String expression,
            StandardEvaluationContext context,
            Class<T> resultType
    ) {
        return evaluate(
                parse(expression),
                context,
                resultType
        );
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void requireContext(
            StandardEvaluationContext context
    ) {
        Assert.notNull(context, "StandardEvaluationContext must not be null");
    }

    private static void requireExpression(
            Expression expression
    ) {
        Assert.notNull(expression, "Expression must not be null");
    }
}