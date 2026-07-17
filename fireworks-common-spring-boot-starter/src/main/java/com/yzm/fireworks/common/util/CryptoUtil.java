package com.yzm.fireworks.common.util;

import org.springframework.util.Assert;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 可逆加解密工具类
 *
 * <p>职责：
 * <ul>
 *   <li>RSA 非对称加解密</li>
 * </ul>
 *
 * <p><b>使用场景建议：</b>
 * <pre>
 * 场景                    推荐方案
 * ────────────────────────────────────────────────
 * 密码存储                PasswordUtil（BCrypt）
 * 手机号/邮箱查询哈希      HashUtil.hashWithSalt
 * 接口报文加密（前后端）   RSA 公钥加密 + 私钥解密（本类）
 * </pre>
 *
 * <p><b>线程安全说明：</b>
 * {@code Cipher} 和 {@code KeyFactory} 均为有状态对象，不能共享静态实例。
 * 本类所有方法在调用时创建独立实例，天然线程安全。
 *
 * @author JYuan
 */
public final class CryptoUtil {

    /**
     * 显式指定填充模式，避免依赖 JDK 默认值。
     *
     * <p>为什么用 OAEP 而不是 PKCS1Padding：
     * PKCS1Padding 存在 Bleichenbacher 攻击漏洞（自 1998 年已知），
     * OAEP（最优非对称加密填充）是目前 RSA 加密的推荐标准。
     */
    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String RSA = "RSA";

    private CryptoUtil() {
        throw new AssertionError("Utility class");
    }

    // ── RSA 加解密 ─────────────────────────────────────────────────────────

    /**
     * 公钥加密（前端或调用方加密敏感数据，服务端用私钥解密）
     *
     * <p>典型场景：前端登录时用公钥加密密码，服务端用私钥解密后做校验。
     *
     * @param plaintext 明文
     * @param publicKey Base64 编码的公钥
     * @return Base64 编码的密文
     */
    public static String encryptByPublicKey(String plaintext, String publicKey) {
        Assert.hasText(plaintext, "plaintext must not be blank");
        Assert.hasText(publicKey, "publicKey must not be blank");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            PublicKey key = keyFactory.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("RSA encrypt by public key failed", e);
        }
    }

    /**
     * 私钥解密（服务端解密用公钥加密的数据）
     *
     * @param ciphertext Base64 编码的密文
     * @param privateKey Base64 编码的私钥
     * @return 明文
     */
    public static String decryptByPrivateKey(String ciphertext, String privateKey) {
        Assert.hasText(ciphertext, "ciphertext must not be blank");
        Assert.hasText(privateKey, "privateKey must not be blank");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            PrivateKey key = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(
                    cipher.doFinal(Base64.getDecoder().decode(ciphertext)),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("RSA decrypt by private key failed", e);
        }
    }

    /**
     * 生成 RSA 密钥对（仅在初始化时调用一次，结果存入配置中心）
     *
     * <p>生成后将公钥配置到前端，私钥存入 Nacos，不可泄露。
     *
     * @param keySize 密钥长度，必须 >= 2048（1024 已不安全）
     */
    public static void generateKeyPair(int keySize) {
        Assert.isTrue(keySize >= 2048, "keySize must be >= 2048");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA);
            generator.initialize(keySize);
            KeyPair keyPair = generator.generateKeyPair();
            System.out.println("PublicKey : " +
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            System.out.println("PrivateKey: " +
                    Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation failed", e);
        }
    }
}