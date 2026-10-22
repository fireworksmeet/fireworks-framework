package com.yzm.fireworks.token.core;

/**
 * 令牌校验失败异常。
 * <p>
 * 消息只包含错误码与可安全外传的上下文（如 {@code kid}），不包含令牌原文与密钥材料。
 *
 * @author JYuan
 */
public class TokenValidationException extends RuntimeException {

    private final TokenErrorCode code;

    private TokenValidationException(TokenErrorCode code, String detail, Throwable cause) {
        super(buildMessage(code, detail), cause);
        this.code = code;
    }

    public static TokenValidationException of(TokenErrorCode code, String detail) {
        return new TokenValidationException(code, detail, null);
    }

    public static TokenValidationException of(TokenErrorCode code, String detail, Throwable cause) {
        return new TokenValidationException(code, detail, cause);
    }

    private static String buildMessage(TokenErrorCode code, String detail) {
        return detail == null || detail.isBlank()
                ? "token verification failed: " + code
                : "token verification failed: " + code + " (" + detail + ")";
    }

    /**
     * 失败原因，用于日志、指标与排障；对外统一映射 401。
     */
    public TokenErrorCode code() {
        return code;
    }
}
