package com.yzm.fireworks.common.util.crypto.model;

/**
 * RSA 密钥对封装类 (Base64 编码)
 *
 * @param publicKey  Base64 编码的 X.509 公钥
 * @param privateKey Base64 编码的 PKCS#8 私钥
 */
public record RsaKeyPair(String publicKey, String privateKey) {
}