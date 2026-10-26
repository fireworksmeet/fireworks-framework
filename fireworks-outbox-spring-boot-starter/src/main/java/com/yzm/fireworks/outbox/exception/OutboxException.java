package com.yzm.fireworks.outbox.exception;

import java.io.Serial;

/**
 * Outbox 基础设施异常。
 * <p>
 * 用于表达「框架自身无法完成动作」（如载荷序列化失败、SQL 约束冲突），
 * 与业务 Handler 抛出的异常区分开：后者由 Dispatcher 按重试策略处理。
 *
 * @author JYuan
 */
public class OutboxException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OutboxException(String message) {
        super(message);
    }

    public OutboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
