package com.yzm.fireworks.oplog.support.parse;

import com.yzm.fireworks.common.expression.SpelEvaluationContext;
import com.yzm.fireworks.common.expression.SpelEvaluator;
import com.yzm.fireworks.common.expression.SpelTemplateRenderer;
import com.yzm.fireworks.oplog.beans.LogRecordOps;
import com.yzm.fireworks.oplog.beans.MethodExecuteResult;
import com.yzm.fireworks.oplog.beans.Operator;
import com.yzm.fireworks.oplog.context.LogRecordContext;
import com.yzm.fireworks.oplog.service.IOperatorGetService;
import lombok.Setter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志模板解析器
 * <p>
 * 求值能力复用 common 的 {@link SpelEvaluator}（带表达式缓存），本类只负责
 * 与操作日志相关的两件事：
 * <ul>
 *     <li>把 {@link LogRecordContext} 中的业务变量注入求值上下文</li>
 *     <li>按 {@code #{表达式}} 占位符渲染模板文案</li>
 * </ul>
 * <p>
 * 模板语法与可用变量参见 {@link SpelTemplateRenderer} 与
 * {@link SpelEvaluationContext}。
 *
 * @author mzt.
 */
public class LogRecordValueParser extends SpelEvaluator implements BeanFactoryAware {

    protected BeanFactory beanFactory;

    /**
     * 操作人获取服务，未指定 {@code operator} 表达式时用于取当前登录人
     */
    @Setter
    protected IOperatorGetService operatorGetService;

    /**
     * 是否跳过"未真正渲染"的日志
     * <p>
     * 由配置项 {@code fireworks.oplog.record.skip-unrendered-log} 注入。
     */
    protected boolean skipUnrenderedLog;

    /**
     * 批量求值模板
     *
     * @param templates           模板集合
     * @param methodExecuteResult 方法执行结果
     * @return 模板原文 → 求值结果的映射（保留原始类型，不做字符串化）
     */
    public Map<String, Object> processTemplate(Collection<String> templates, MethodExecuteResult methodExecuteResult) {
        Map<String, Object> expressionValues = new HashMap<>(templates.size());
        EvaluationContext evaluationContext = createMethodContext(methodExecuteResult);
        AnnotatedElementKey methodKey =
                methodKey(methodExecuteResult.getMethod(), methodExecuteResult.getTargetClass());

        for (String template : templates) {
            expressionValues.put(template, render(template, methodKey, evaluationContext));
        }
        return expressionValues;
    }

    /**
     * 求值操作人
     * <p>
     * {@code @LogRecord#operator()} 指定了表达式时，其求值结果<b>必须</b>为
     * {@link Operator} 对象，此时直接采用该结果，<b>不再调用</b>
     * {@link com.yzm.fireworks.oplog.service.IOperatorGetService}，
     * 避免"业务已给出操作人却仍查询一次"的重复开销。
     * <p>
     * 未指定表达式时，回退到 {@code IOperatorGetService} 获取当前登录人。
     *
     * @param operation        注解元数据
     * @param expressionValues 已求值的模板结果
     * @return 操作人
     */
    public Operator resolveOperator(LogRecordOps operation, Map<String, Object> expressionValues) {
        if (StringUtils.hasText(operation.getOperator())) {
            Object value = expressionValues.get(operation.getOperator());
            if (value instanceof Operator operator) {
                return operator;
            }
            // 表达式配置错误属于业务书写问题，明确抛出而非静默兜底，避免写入错误操作人
            throw new IllegalStateException("LogRecord 注解 operator 表达式的求值结果必须是 Operator，"
                    + "实际为 " + (value == null ? "null" : value.getClass().getName())
                    + "，表达式：" + operation.getOperator());
        }
        // 未指定 operator 表达式，回退到服务获取当前登录人。
        // 若业务未实现 IOperatorGetService，默认实现会直接抛出异常提示配置方式。
        Operator operator = operatorGetService.getUser();
        if (operator == null || operator.getOperatorId() == null) {
            throw new IllegalStateException("IOperatorGetService 返回的操作人为空，"
                    + "请检查实现是否正确返回当前登录用户");
        }
        return operator;
    }

    /**
     * 以布尔语义求值条件模板
     * <p>
     * 条件模板形如 {@code "#{#_result != null}"}，整个模板即一个表达式，
     * 因此直接按布尔类型求值，避免「先渲染成字符串再比较」带来的误判。
     *
     * @param methodExecuteResult 方法执行结果
     * @param template            条件模板
     * @return 布尔结果；模板不是纯表达式或结果为空时返回 {@code null}
     */
    @Nullable
    public Boolean evaluateCondition(MethodExecuteResult methodExecuteResult, String template) {
        String expression = SpelTemplateRenderer.asSingleExpression(template);
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        EvaluationContext evaluationContext = createMethodContext(methodExecuteResult);
        AnnotatedElementKey methodKey =
                methodKey(methodExecuteResult.getMethod(), methodExecuteResult.getTargetClass());
        return evaluateBoolean(expression, methodKey, evaluationContext);
    }

    /**
     * 创建方法级求值上下文
     * <p>
     * 在 common 的上下文基础上注入操作日志特有的变量：
     * <ul>
     *     <li>{@code #自定义名}：业务通过 {@link LogRecordContext#putVariable} 放入的变量</li>
     *     <li>{@code #全局名}：业务通过 {@link LogRecordContext#putGlobalVariable} 放入的变量</li>
     *     <li>{@code #_result} / {@code #_exception}：由父类统一设置</li>
     * </ul>
     */
    private EvaluationContext createMethodContext(MethodExecuteResult methodExecuteResult) {
        Method method = methodExecuteResult.getMethod();
        Class<?> targetClass = methodExecuteResult.getTargetClass();
        Method targetMethod = resolveTargetMethod(method, targetClass);
        Object[] args = methodExecuteResult.getArgs();

        SpelEvaluationContext context = new SpelEvaluationContext(
                null,
                targetMethod,
                args == null ? new Object[0] : args,
                getParameterNameDiscoverer(),
                methodExecuteResult.getResult(),
                methodExecuteResult.getThrowable());
        if (beanFactory != null) {
            context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        }

        // 业务变量优先级最高，最后注入以覆盖同名变量
        Map<String, Object> variables = LogRecordContext.getVariables();
        if (!variables.isEmpty()) {
            context.setVariables(variables);
        }
        Map<String, Object> globalVariables = LogRecordContext.getGlobalVariableMap();
        if (globalVariables != null && !globalVariables.isEmpty()) {
            for (Map.Entry<String, Object> entry : globalVariables.entrySet()) {
                // 方法级变量优先，全局变量仅在不存在时补位
                if (context.lookupVariable(entry.getKey()) == null) {
                    context.setVariable(entry.getKey(), entry.getValue());
                }
            }
        }
        return context;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

}
