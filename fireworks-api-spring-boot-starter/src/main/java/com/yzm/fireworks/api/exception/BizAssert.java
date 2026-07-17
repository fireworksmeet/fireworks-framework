package com.yzm.fireworks.api.exception;


import com.yzm.fireworks.api.Result;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import org.jetbrains.annotations.Contract;
import org.springframework.lang.Nullable;

/**
 * @author JYuan
 * 业务断言
 */
public class BizAssert {

    @Contract("false, _ -> fail")
    public static void isTrue(boolean expression, ExceptionStatus status) {
        if (!expression) {
            throw new BizException(status);
        }
    }

    @Contract("true, _ -> fail")
    public static void isFalse(boolean expression, ExceptionStatus status) {
        isTrue(!expression, status);
    }

    @Contract("!null, _ -> fail")
    public static void isNull(@Nullable Object object, ExceptionStatus status) {
        isTrue(object == null, status);
    }

    @Contract("null, _ -> fail")
    public static void notNull(@Nullable Object object, ExceptionStatus status) {
        isTrue(object != null, status);
    }

    public static void isTrue(Result<?> result) {
        if (result.fail()) {
            throw new BizException(result);
        }
    }

    @Contract("false, _ -> fail")
    public static void isTrue(boolean expression, Result<?> result) {
        if (!expression) {
            throw new BizException(result);
        }
    }

    @Contract("_ -> fail")
    public static void fail(ExceptionStatus status) {
        throw new BizException(status);
    }

    @Contract("_ -> fail")
    public static void fail(Result<?> result) {
        throw new BizException(result);
    }

    @Contract("_, _ -> fail")
    public static void fail(ExceptionStatus status, String msg) {
        throw new BizException(status, msg);
    }

}
