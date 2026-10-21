package com.yzm.fireworks.message.exception;

import java.io.Serial;

/**
 * 入参校验失败。
 * <p>
 * 由门面或 {@code MessageProvider#convert} 抛出，统一转换为 {@code PermanentFailure}：
 * 参数错误重试不会变好。
 *
 * @author JYuan
 */
public class MessageValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MessageValidationException(String message) {
        super(message);
    }
}
