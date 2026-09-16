package com.yzm.fireworks.common.expression;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpEL 求值器基类
 * <p>
 * 面向「注解驱动」场景的表达式求值内核，建立在 Spring 官方
 * {@link CachedExpressionEvaluator} 之上，提供两项基础能力：
 * <ul>
 *     <li><b>表达式缓存</b>：以（方法，目标类，表达式文本）为键缓存解析结果。
 *         {@link Expression} 不可变且线程安全，可安全共享；求值上下文每次新建，
 *         因此缓存不会导致「取值被固定」。</li>
 *     <li><b>上下文构建</b>：统一基于 {@code MethodBasedEvaluationContext}，
 *         原生提供 {@code #参数名}、{@code #a0}、{@code #p0} 等变量。</li>
 * </ul>
 * <p>
 * <b>变量约定</b>：本类不定义任何业务语义的变量（如返回值、异常、业务上下文）。
 * 子类通过覆写 {@link #createContext} 注入自己的变量，使求值器保持与具体场景解耦。
 * <p>
 * <b>与 {@code @Value} / 配置占位符的区别</b>：本类求值的是 <b>SpEL 表达式</b>，
 * 表达式中的 {@code #{}} 不会被 Spring 的属性占位符机制处理。
 *
 * @author JYuan
 */
public class SpelEvaluator extends CachedExpressionEvaluator {

    /**
     * 已解析 {@link Expression} 的缓存。
     * <p>
     * 键包含方法、目标类与表达式文本，因此不同方法上的同名表达式会分别缓存。
     */
    private final Map<ExpressionKey, Expression> expressionCache = new ConcurrentHashMap<>(64);

    /**
     * 已解析「模板」的缓存。
     * <p>
     * 模板需用 {@code TemplateParserContext} 解析，与普通表达式不可共用缓存，
     * 因此单列一个缓存，键为「方法 + 模板文本」。
     */
    private final Map<TemplateKey, Expression> templateCache = new ConcurrentHashMap<>(64);

    /**
     * 方法 → 最具体方法 的映射缓存。
     * <p>
     * 用于把接口方法桥接到实现类方法，使参数名发现器能够正常工作。
     */
    private final Map<AnnotatedElementKey, Method> targetMethodCache = new ConcurrentHashMap<>(64);

    /**
     * 求值 SpEL 表达式
     *
     * @param expression  SpEL 表达式文本
     * @param methodKey   表达式所属的方法（方法 + 目标类），作为缓存键的一部分
     * @param evalContext 求值上下文
     * @return 求值结果；表达式为空时返回 {@code null}
     */
    @Nullable
    public Object evaluate(@Nullable String expression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
        if (expression == null) {
            return null;
        }
        return getExpression(this.expressionCache, methodKey, expression).getValue(evalContext, Object.class);
    }

    /**
     * 以指定类型求值 SpEL 表达式
     * <p>
     * 类型转换交由 SpEL 自身的转换机制完成，转换失败会抛出异常。
     *
     * @param expression  SpEL 表达式文本
     * @param methodKey   表达式所属的方法（方法 + 目标类）
     * @param evalContext 求值上下文
     * @param resultType  期望的结果类型
     * @param <T>         结果类型
     * @return 求值结果；表达式为空时返回 {@code null}
     */
    @Nullable
    public <T> T evaluate(@Nullable String expression, AnnotatedElementKey methodKey,
                          EvaluationContext evalContext, Class<T> resultType) {
        if (expression == null) {
            return null;
        }
        return getExpression(this.expressionCache, methodKey, expression).getValue(evalContext, resultType);
    }

    /**
     * 以布尔语义求值 SpEL 表达式
     * <p>
     * 用于条件判定场景。非布尔结果会按字符串语义兜底转换，
     * 避免调用方自行做「转字符串再比较」的错误判定。
     *
     * @param expression  SpEL 表达式文本
     * @param methodKey   表达式所属的方法（方法 + 目标类）
     * @param evalContext 求值上下文
     * @return 布尔结果；表达式为空或求值为 {@code null} 时返回 {@code null}
     */
    @Nullable
    public Boolean evaluateBoolean(@Nullable String expression, AnnotatedElementKey methodKey,
                                   EvaluationContext evalContext) {
        if (expression == null) {
            return null;
        }
        Object value = evaluate(expression, methodKey, evalContext);
        if (value instanceof Boolean b) {
            return b;
        }
        return value == null ? null : Boolean.parseBoolean(value.toString().trim());
    }

    /**
     * 求值模板
     * <p>
     * 模板形态决定返回值类型：
     * <ul>
     *     <li><b>单个占位符</b>（如 {@code "#{T(com.xxx.UserContext).getUser()}"}）：
     *         返回<b>求值结果的原始类型</b>，不做字符串化。
     *         因此表达式返回 {@code Operator}、{@code Long}、{@code Boolean}
     *         等对象时，调用方可直接按类型判断使用</li>
     *     <li><b>混排模板</b>（如 {@code "用户 #{#name} 登录"}）：只能拼成字符串，
     *         返回 {@link String}；表达式求值为 {@code null} 时渲染为空串</li>
     *     <li><b>纯静态文案</b>（无占位符）：原样返回 {@link String}，跳过解析与求值</li>
     * </ul>
     * <p>
     * 之所以不统一返回字符串：字符串化会丢失类型信息（如 {@code Operator} 会被
     * {@code toString()} 成 {@code "Operator(operatorId=1, operatorName=张三)"}），
     * 使调用方无法还原对象。渲染为字符串是<b>调用方的消费需求</b>，
     * 应由调用方在边界处自行转换，而非由求值内核代劳。
     *
     * @param template    模板字符串，可为 {@code null}
     * @param methodKey   表达式所属的方法（方法 + 目标类），作为缓存键的一部分
     * @param evalContext 求值上下文
     * @return 求值结果；模板为 {@code null} 时返回 {@code null}
     */
    @Nullable
    public Object render(@Nullable String template, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
        if (template == null) {
            return null;
        }
        String expression = SpelTemplateRenderer.asSingleExpression(template);
        if (expression != null) {
            // 单表达式：保留原始类型
            return evaluate(expression, methodKey, evalContext);
        }
        if (!SpelTemplateRenderer.containsPlaceholder(template)) {
            // 纯静态文案：直接原样返回
            return template;
        }
        TemplateKey templateKey = new TemplateKey(methodKey, template);
        Expression composite = this.templateCache.computeIfAbsent(templateKey,
                key -> SpelTemplateRenderer.parse(template));
        Object value = composite.getValue(evalContext);
        return value == null ? "" : value.toString();
    }

    /**
     * 渲染模板为字符串
     * <p>
     * {@link #render} 的字符串视图，用于「调用方明确需要字符串」的场景，
     * 如日志文案落库、锁 key 拼接。求值结果为 {@code null} 时返回 {@code null}。
     *
     * @param template    模板字符串，可为 {@code null}
     * @param methodKey   表达式所属的方法（方法 + 目标类）
     * @param evalContext 求值上下文
     * @return 字符串结果；模板为 {@code null} 或求值为 {@code null} 时返回 {@code null}
     */
    @Nullable
    public String renderToString(@Nullable String template, AnnotatedElementKey methodKey,
                                 EvaluationContext evalContext) {
        Object value = render(template, methodKey, evalContext);
        return value == null ? null : value.toString();
    }

    /**
     * 构建表达式所属方法的缓存键
     *
     * @param method      当前方法
     * @param targetClass 目标类
     */
    public AnnotatedElementKey methodKey(Method method, Class<?> targetClass) {
        return new AnnotatedElementKey(method, targetClass);
    }

    /**
     * 创建方法级求值上下文
     * <p>
     * 供子类构建自己的上下文，或直接使用于无需额外变量的场景。
     *
     * @param method      当前方法
     * @param args        方法参数，可为 {@code null}
     * @param targetClass 目标类，可为 {@code null}
     * @param result      方法返回值，可为 {@code null}
     * @param exception   方法异常，可为 {@code null}
     * @param beanFactory Spring BeanFactory，可为 {@code null}
     * @return 求值上下文
     */
    public SpelEvaluationContext createContext(@Nullable Method method, @Nullable Object[] args,
                                               @Nullable Class<?> targetClass, @Nullable Object result,
                                               @Nullable Throwable exception, @Nullable BeanFactory beanFactory) {
        Method targetMethod = resolveTargetMethod(method, targetClass);
        Object[] actualArgs = args == null ? new Object[0] : args;
        SpelEvaluationContext context = new SpelEvaluationContext(
                null, targetMethod, actualArgs, getParameterNameDiscoverer(), result, exception);
        if (beanFactory != null) {
            context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        }
        return context;
    }

    /**
     * 模板缓存键：方法 + 模板文本
     */
    private record TemplateKey(AnnotatedElementKey methodKey, String template) {
    }

    /**
     * 解析最具体方法
     * <p>
     * 接口方法需要桥接到实现类方法，否则参数名发现器无法解析参数名。
     * 结果按（方法，目标类）缓存。
     */
    protected Method resolveTargetMethod(@Nullable Method method, @Nullable Class<?> targetClass) {
        if (method == null) {
            return null;
        }
        Class<?> clazz = targetClass == null ? method.getDeclaringClass() : targetClass;
        AnnotatedElementKey methodKey = new AnnotatedElementKey(method, clazz);
        return targetMethodCache.computeIfAbsent(methodKey, k -> AopUtils.getMostSpecificMethod(method, clazz));
    }
}
