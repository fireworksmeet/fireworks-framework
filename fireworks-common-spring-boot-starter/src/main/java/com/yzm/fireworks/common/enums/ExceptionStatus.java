package com.yzm.fireworks.common.enums;

import org.springframework.util.Assert;

import java.util.HashSet;
import java.util.Set;

/**
 * @author JYuan
 * 异常状态
 * <p>
 * 【刻意不携带 HTTP 状态码】错误码只表达<b>业务语义</b>，HTTP 状态码由<b>异常类型</b>在全局异常处理器中决定：
 * <ul>
 *   <li>业务异常（{@code BizException}）一律返回 {@code 200}——请求已被正确接收和处理，结果是业务拒绝，
 *       由响应体中的 {@code code} 表达精确原因；</li>
 *   <li>协议 / 容器 / 系统层异常才使用 4xx / 5xx，它们不是业务语义的一部分。</li>
 * </ul>
 * <p>
 * 让每个错误码各自声明 HTTP 状态码，会把协议层决策下沉到业务语义声明中，带来三个问题：
 * 同一错误码在不同接口可能需要不同 HTTP 语义却无法表达；业务开发者必须记住 400 / 409 / 503 的取舍；
 * 任何需要转发 HTTP 状态码的实现（如 {@link #format(Object...)} 的匿名子类）都会漏掉这一步。
 * 两个维度分属两层，各自演进才是稳定的。
 */
public interface ExceptionStatus {
    /**
     * 异常状态编码
     */
    int getCode();

    /**
     * 异常消息
     */
    String getMessage();

    default ExceptionStatus format(Object... args) {
        return new ExceptionStatus() {
            @Override
            public int getCode() {
                return ExceptionStatus.this.getCode();
            }

            @Override
            public String getMessage() {
                return String.format(ExceptionStatus.this.getMessage(), args);
            }
        };
    }


    /**
     * 校验业务异常状态码是否唯一
     *
     * @param clazz 业务异常状态枚举类
     */
    static <T extends ExceptionStatus> void checkCodeUnique(Class<T> clazz) {
        T[] values = clazz.getEnumConstants();
        Set<Integer> codeSet = new HashSet<>();
        for (T value : values) {
            int code = value.getCode();
            Assert.isTrue(codeSet.add(code), value.getClass().getName() + "存在重复状态码: " + code);
        }
    }
}
