package com.yzm.fireworks.oplog.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author muzhantong
 */
@Repeatable(LogRecords.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface LogRecord {
    /**
     * 方法执行成功后的日志模版
     * <p>
     * 动态部分用 {@code #{SpEL 表达式}} 占位，可与静态文案混排，如：
     * {@code "修改用户：#{#user.name}"}
     *
     * @return 成功日志模版
     */
    String success();

    /**
     * 方法执行失败后的日志模版
     * <p>
     * 为空表示失败时不记录日志
     *
     * @return 失败日志模版
     */
    String fail() default "";

    /**
     * 操作人 ID 的 SpEL 表达式模版
     * <p>
     * 为空时操作人 ID 取自 {@code IOperatorGetService} 返回的当前登录用户；
     * 操作人姓名快照始终取自 {@code IOperatorGetService}
     *
     * @return 操作人 ID 表达式，如 {@code "#{#user.id}"}
     */
    String operator() default "";

    /**
     * @return 操作日志的类型，比如：订单类型、商品类型
     */
    String type();

    /**
     * @return 日志的子类型，比如订单的C端日志，和订单的B端日志，type都是订单类型，但是子类型不一样
     */
    String subType() default "";

    /**
     * @return 日志绑定的业务标识
     */
    String bizNo();

    /**
     * @return 日志的额外信息
     */
    String extra() default "";

    /**
     * 是否记录日志的条件
     * <p>
     * 按布尔语义求值，为 false 时跳过记录，如 {@code "#{#_result != null}"}
     *
     * @return 条件表达式，为空时默认记录
     */
    String condition() default "";

    /**
     * 记录成功日志的条件
     * <p>
     * 按布尔语义求值。为空时以方法是否抛异常判定成功
     *
     * @return 表示成功的表达式，默认为空，代表不抛异常为成功
     */
    String successCondition() default "";
}
