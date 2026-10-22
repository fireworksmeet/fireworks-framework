package com.yzm.fireworks.token.core;

/**
 * JWT 签发。
 * <p>
 * 纯函数语义：不读写 {@code ThreadLocal}、不访问数据库、不产生除日志与指标外的副作用。
 * 实现必须线程安全。
 *
 * @author JYuan
 */
public interface JwtIssuer {

    /**
     * 签发一个紧凑序列化的 JWS。
     *
     * @param spec 签发请求
     * @return {@code header.payload.signature}
     * @throws TokenConfigurationException 没有可用的签发密钥，或密钥与算法不匹配
     * @throws IllegalArgumentException    请求非法（缺主体、TTL 越界、保留声明冲突）
     */
    String issue(TokenSpec spec);
}
