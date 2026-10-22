package com.yzm.fireworks.token.core;

/**
 * 令牌模块的部署/配置问题（密钥缺失、材料无法解析、算法与密钥不匹配等）。
 * <p>
 * 与 {@link TokenValidationException} 严格区分：本异常表示"我们配错了"，不是"用户的凭证坏了"，
 * 应当在启动期或首次使用时立刻暴露。
 *
 * @author JYuan
 */
public class TokenConfigurationException extends RuntimeException {

    public TokenConfigurationException(String message) {
        super(message);
    }

    public TokenConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
