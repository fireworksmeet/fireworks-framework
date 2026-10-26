package com.yzm.fireworks.outbox.exception;

import java.io.Serial;

/**
 * 不可重试异常标记。
 * <p>
 * Handler 抛出本异常（可以是任意层级的原因）表示「重试没有意义」，Dispatcher 会直接把事件
 * 置为 {@code DEAD}，不再消耗重试次数。
 * <p>
 * 典型场景：参数非法、权限不足、业务状态非法、下游明确返回「请求不可能成功」。
 * 与之相对，连接失败、网络超时、下游暂时不可用属于可重试，直接抛普通异常即可。
 *
 * @author JYuan
 */
public class OutboxNonRetryableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OutboxNonRetryableException(String message) {
        super(message);
    }

    public OutboxNonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
