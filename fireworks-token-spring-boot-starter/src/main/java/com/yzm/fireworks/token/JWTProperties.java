package com.yzm.fireworks.token;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;


/**
 * JWT 配置属性
 * <p>
 * 已在 {@link TokenAutoConfiguration} 中通过
 * {@code @EnableConfigurationProperties(JWTProperties.class)} 正确激活。
 *
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.token.jwt")
@Data
public class JWTProperties {
    /**
     * 密钥（必须配置，不能为空，且长度不低于 32 位）
     */
    private String secret;

    @PostConstruct
    public void validate() {
        Assert.hasText(secret,
                "JWT secret must be configured! Please set 'fireworks.token.jwt.secret' in your configuration.");
        Assert.isTrue(secret.length() >= 32,
                "JWT secret must be at least 32 characters for security.");
    }

    /**
     * jwt 签发者
     */
    private String iss = Constant.AUTHOR;

    /**
     * 接收 jwt 的一方
     */
    private String aud = Constant.VENDOR;

    /**
     * 存储 uid 的 key
     */
    private String userIdKey = Constant.USER_ID;

    /**
     * 扩展字段
     */
    private String extKey = Constant.EXT;
}