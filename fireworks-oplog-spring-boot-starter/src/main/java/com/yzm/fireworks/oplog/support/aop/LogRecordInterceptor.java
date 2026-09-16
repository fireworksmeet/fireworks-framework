package com.yzm.fireworks.oplog.support.aop;

import com.yzm.fireworks.common.expression.SpelTemplateRenderer;
import com.yzm.fireworks.oplog.beans.CodeVariableType;
import com.yzm.fireworks.oplog.beans.LogRecord;
import com.yzm.fireworks.oplog.beans.LogRecordOps;
import com.yzm.fireworks.oplog.beans.MethodExecuteResult;
import com.yzm.fireworks.oplog.beans.Operator;
import com.yzm.fireworks.oplog.context.LogRecordContext;
import com.yzm.fireworks.oplog.service.ILogRecordService;
import com.yzm.fireworks.oplog.service.IOperatorGetService;
import com.yzm.fireworks.oplog.support.parse.LogRecordValueParser;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 操作日志拦截器
 * <p>
 * 拦截标注了 {@code @LogRecord} 的方法，在方法执行后解析注解模板并落库。
 * <p>
 * 求值所需的方法执行前信息（如数据库旧值），由业务通过
 * {@link LogRecordContext#putVariable(String, Object)} 在执行前自行放入上下文。
 *
 * @author mzt.
 */
@Slf4j
public class LogRecordInterceptor extends LogRecordValueParser implements MethodInterceptor, Serializable, SmartInitializingSingleton {

    @Setter
    private LogRecordOperationSource logRecordOperationSource;

    private String tenantId;

    @Setter
    private ILogRecordService bizLogService;

    @Setter
    private boolean joinTransaction;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        return execute(invocation, invocation.getThis(), method, invocation.getArguments());
    }

    private Object execute(MethodInvocation invoker, Object target, Method method, Object[] args) throws Throwable {
        //代理不拦截
        if (AopUtils.isAopProxy(target)) {
            return invoker.proceed();
        }
        Class<?> targetClass = getTargetClass(target);
        Object ret = null;
        MethodExecuteResult methodExecuteResult = new MethodExecuteResult(method, args, targetClass);
        LogRecordContext.putEmptySpan();
        Collection<LogRecordOps> operations = new ArrayList<>();
        try {
            Collection<LogRecordOps> metadata = logRecordOperationSource.getMetadata(method, targetClass);
            operations = metadata == null ? new ArrayList<>() : metadata;
        } catch (Exception e) {
            log.error("log record parse before function exception", e);
        }

        try {
            ret = invoker.proceed();
            methodExecuteResult.setResult(ret);
            methodExecuteResult.setSuccess(true);
        } catch (Exception e) {
            methodExecuteResult.setSuccess(false);
            methodExecuteResult.setThrowable(e);
        } catch (Error e) {
            // Error（如 OOM）也必须回收上下文，否则 ThreadLocal 会泄漏
            LogRecordContext.clear();
            throw e;
        }

        try {
            if (!CollectionUtils.isEmpty(operations)) {
                recordExecute(methodExecuteResult, operations);
            }
        } catch (Exception t) {
            log.error("log record parse exception", t);
            // 仅当日志与业务同一事务时，才让日志失败影响业务（触发整体回滚）
            if (joinTransaction) {
                // 业务本身已失败时，优先抛出业务异常，避免日志异常掩盖真实失败原因
                if (methodExecuteResult.getThrowable() != null) {
                    throw methodExecuteResult.getThrowable();
                }
                throw t;
            }
        } finally {
            LogRecordContext.clear();
        }

        if (methodExecuteResult.getThrowable() != null) {
            throw methodExecuteResult.getThrowable();
        }
        return ret;
    }

    /**
     * 逐条记录日志
     * <p>
     * 本方法不捕获异常，任何一条记录的失败都会向上冒泡至 {@link #execute}，
     * 由 {@code execute} 统一决定是否影响业务事务并记录日志，
     * 避免在两层重复判定 {@code joinTransaction} 与重复打印异常。
     */
    private void recordExecute(MethodExecuteResult methodExecuteResult, Collection<LogRecordOps> operations) {
        for (LogRecordOps operation : operations) {
            if (!StringUtils.hasText(operation.getSuccessLogTemplate())
                    && !StringUtils.hasText(operation.getFailLogTemplate())) {
                continue;
            }
            if (exitsCondition(methodExecuteResult, operation)) {
                continue;
            }
            if (!methodExecuteResult.isSuccess()) {
                failRecordExecute(methodExecuteResult, operation);
            } else {
                successRecordExecute(methodExecuteResult, operation);
            }
        }
    }

    private void successRecordExecute(MethodExecuteResult methodExecuteResult, LogRecordOps operation) {
        // 若存在 isSuccess 条件模版，解析出成功/失败的模版
        String action;
        boolean flag = true;
        if (StringUtils.hasText(operation.getIsSuccess())) {
            Boolean success = evaluateCondition(methodExecuteResult, operation.getIsSuccess());
            if (Boolean.TRUE.equals(success)) {
                action = operation.getSuccessLogTemplate();
            } else {
                action = operation.getFailLogTemplate();
                flag = false;
            }
        } else {
            action = operation.getSuccessLogTemplate();
        }
        if (!StringUtils.hasText(action)) {
            // 没有日志内容则忽略
            return;
        }
        Map<String, Object> expressionValues = processTemplate(buildTemplates(operation, action), methodExecuteResult);
        Operator operator = resolveOperator(operation, expressionValues);
        saveLog(methodExecuteResult.getMethod(), !flag, operation, operator, action, expressionValues);
    }

    private void failRecordExecute(MethodExecuteResult methodExecuteResult, LogRecordOps operation) {
        if (!StringUtils.hasText(operation.getFailLogTemplate())) {
            return;
        }

        String action = operation.getFailLogTemplate();
        Map<String, Object> expressionValues = processTemplate(buildTemplates(operation, action), methodExecuteResult);
        Operator operator = resolveOperator(operation, expressionValues);
        saveLog(methodExecuteResult.getMethod(), true, operation, operator, action, expressionValues);
    }

    /**
     * 求值 condition，返回 true 表示应当跳过本次记录
     */
    private boolean exitsCondition(MethodExecuteResult methodExecuteResult, LogRecordOps operation) {
        if (!StringUtils.hasText(operation.getCondition())) {
            return false;
        }
        return Boolean.FALSE.equals(evaluateCondition(methodExecuteResult, operation.getCondition()));
    }

    private void saveLog(Method method, boolean flag, LogRecordOps operation, Operator operator,
                         String action, Map<String, Object> expressionValues) {
        Object actionValue = expressionValues.get(action);
        if (!StringUtils.hasText(asString(actionValue))
                || (!skipUnrenderedLog && SpelTemplateRenderer.containsPlaceholder(action)
                    && Objects.equals(action, actionValue))) {
            return;
        }
        LogRecord logRecord = LogRecord.builder()
                .tenant(tenantId)
                .type(asString(expressionValues.get(operation.getType())))
                .bizNo(asString(expressionValues.get(operation.getBizNo())))
                .operator(operator)
                .subType(asString(expressionValues.get(operation.getSubType())))
                .extra(asString(expressionValues.get(operation.getExtra())))
                .codeVariable(getCodeVariable(method))
                .action(asString(actionValue))
                .fail(flag)
                .createdAt(Instant.now())
                .build();

        bizLogService.save(logRecord);
    }

    /**
     * 将求值结果转为字符串
     * <p>
     * 求值层保留原始类型，落库字段为字符串，故在边界处统一转换。
     */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Map<CodeVariableType, Object> getCodeVariable(Method method) {
        Map<CodeVariableType, Object> map = new HashMap<>();
        map.put(CodeVariableType.ClassName, method.getDeclaringClass());
        map.put(CodeVariableType.MethodName, method.getName());
        return map;
    }

    /**
     * 收集需要求值的模板：类型、业务标识、子类型、扩展信息、操作人与最终文案
     */
    private List<String> buildTemplates(LogRecordOps operation, String action) {
        List<String> templates = new ArrayList<>();
        templates.add(operation.getType());
        templates.add(operation.getBizNo());
        templates.add(operation.getSubType());
        templates.add(operation.getExtra());
        templates.add(operation.getOperator());
        templates.add(action);
        return templates;
    }

    @Override
    public void afterSingletonsInstantiated() {
        this.setBizLogService(beanFactory.getBean(ILogRecordService.class));
        this.setOperatorGetService(beanFactory.getBean(IOperatorGetService.class));
    }

    public void setTenant(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 设置是否跳过"未真正渲染"的日志
     */
    public void setSkipUnrenderedLog(boolean skipUnrenderedLog) {
        this.skipUnrenderedLog = skipUnrenderedLog;
    }

    private Class<?> getTargetClass(Object target) {
        return AopUtils.getTargetClass(target);
    }
}
