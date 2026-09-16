package com.yzm.fireworks.oplog.beans;

import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Method;

/**
 * 目标方法的执行结果
 * <p>
 * 在 {@code LogRecordInterceptor} 中构造并逐段填充：
 * 构造时确定「执行哪个方法」（method/args/targetClass），
 * 方法执行后再回填「执行结果」（success/result 或 throwable）。
 * <p>
 * 该对象作为 SpEL 求值上下文的数据来源，供日志文案模板引用：
 * 通过 {@link #getResult()} 暴露为 {@code #_result}，
 * 通过 {@link #getThrowable()} 暴露为 {@code #_exception}。
 *
 * @author wulang
 **/
@Getter
@Setter
public class MethodExecuteResult {

    /**
     * 方法是否执行成功
     * <p>
     * 方法正常返回为 true，抛出异常为 false
     */
    private boolean success;

    /**
     * 方法抛出的异常对象
     * <p>
     * 仅执行失败时有值，成功时为 null。
     * 在 SpEL 中通过 {@code #_exception} 引用，可取
     * {@code #_exception.message}、{@code #_exception.class.name}
     */
    private Throwable throwable;

    /**
     * 方法返回值
     * <p>
     * 仅执行成功时有值，失败时为 null。
     * 在 SpEL 中通过 {@code #_result} 引用
     */
    private Object result;

    /**
     * 目标方法（构造时确定，不可变）
     */
    private final Method method;

    /**
     * 方法入参（构造时确定，不可变）
     */
    private final Object[] args;

    /**
     * 目标类（构造时确定，不可变）
     * <p>
     * 用于解析 SpEL 表达式中的方法参数名与桥接方法
     */
    private final Class<?> targetClass;

    /**
     * 构造执行结果对象
     * <p>
     * 此时方法尚未执行，仅确定目标方法相关信息，
     * 执行结果需通过 setter 回填
     *
     * @param method      目标方法
     * @param args        方法入参
     * @param targetClass 目标类
     */
    public MethodExecuteResult(Method method, Object[] args, Class<?> targetClass) {
        this.method = method;
        this.args = args;
        this.targetClass = targetClass;
    }

}
