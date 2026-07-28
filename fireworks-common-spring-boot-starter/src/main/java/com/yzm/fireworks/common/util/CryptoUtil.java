package com.yzm.fireworks.common.util;

import org.springframework.util.Assert;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * 可逆加解密统一工具类
 *
 * <p>职责：
 * <ul>
 *   <li>RSA 非对称加解密（适用于前端/第三方短文本加密交互）</li>
 *   <li>AES 对称加解密（预留，适用于长文本、大文件或数据库字段加密）</li>
 * </ul>
 *
 * <p><b>使用场景建议：</b>
 * <pre>
 * 场景                    推荐方案
 * ────────────────────────────────────────────────
 * 密码存储                PasswordUtil（BCrypt）
 * 手机号/邮箱查询哈希      HashUtil.hashWithSalt
 * 接口敏感数据传递        CryptoUtil.rsaEncrypt / rsaDecrypt
 * 数据库长文本加解密      CryptoUtil.aesEncrypt / aesDecrypt
 * </pre>
 *
 * <p><b>线程安全说明：</b>
 * {@code Cipher} 和 {@code KeyFactory} 均为有状态对象，不能共享静态实例。
 * 本类所有方法在调用时创建独立实例，天然线程安全。
 *
 * @author JYuan
 */
public final class CryptoUtil {

    private static final String RSA = "RSA";
    private static final String RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * 明确指定 OAEP 参数，确保与前端标准 Web Crypto API / JS 加密库完全兼容
     */
    private static final OAEPParameterSpec OAEP_SHA256_SPEC = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    private CryptoUtil() {
        throw new AssertionError("Utility class");
    }

    // ── RSA 非对称加解密 ─────────────────────────────────────────────────────

    /**
     * RSA 公钥加密（通常用于前端或调用方加密敏感数据）
     *
     * <p>注意：RSA/OAEP-2048 单次最大加密明文长度约为 190 字节，仅适用于短文本（如密码、Token）。
     *
     * @param plaintext 明文
     * @param publicKey Base64 编码的公钥
     * @return Base64 编码的密文
     */
    public static String rsaEncrypt(String plaintext, String publicKey) {
        Assert.hasText(plaintext, "plaintext must not be blank");
        Assert.hasText(publicKey, "publicKey must not be blank");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            PublicKey key = keyFactory.generatePublic(
                    new X509EncodedKeySpec(Base64Util.decodeToBytes(publicKey)));

            Cipher cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, OAEP_SHA256_SPEC);

            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64Util.encode(cipherBytes);
        } catch (Exception e) {
            throw new IllegalStateException("RSA encrypt failed", e);
        }
    }

    /**
     * RSA 私钥解密（通常用于服务端解密公钥加密的数据）
     *
     * @param ciphertext Base64 编码的密文
     * @param privateKey Base64 编码的私钥
     * @return 解密后的明文
     */
    public static String rsaDecrypt(String ciphertext, String privateKey) {
        Assert.hasText(ciphertext, "ciphertext must not be blank");
        Assert.hasText(privateKey, "privateKey must not be blank");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            PrivateKey key = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64Util.decodeToBytes(privateKey)));

            Cipher cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, OAEP_SHA256_SPEC);

            byte[] plainBytes = cipher.doFinal(Base64Util.decodeToBytes(ciphertext));
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("RSA decrypt failed", e);
        }
    }

    /**
     * 生成 RSA 密钥对
     *
     * @param keySize 密钥长度，必须 >= 2048
     * @return RsaKeyPairResult 包含 Base64 编码的公钥和私钥
     */
    public static RsaKeyPair generateRsaKeyPair(int keySize) {
        Assert.isTrue(keySize >= 2048, "keySize must be >= 2048");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA);
            generator.initialize(keySize);
            KeyPair keyPair = generator.generateKeyPair();

            String publicKey = Base64Util.encode(keyPair.getPublic().getEncoded());
            String privateKey = Base64Util.encode(keyPair.getPrivate().getEncoded());

            return new RsaKeyPair(publicKey, privateKey);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation failed", e);
        }
    }

    // ── AES 对称加解密（预留扩展位） ──────────────────────────────────────────

    /*
    public static String aesEncrypt(String plaintext, String secretKey) {
        // 后续在此扩展 AES/GCM/NoPadding 加密实现
    }

    public static String aesDecrypt(String ciphertext, String secretKey) {
        // 后续在此扩展 AES/GCM/NoPadding 解密实现
    }
    */

    // ── 结果实体类 ───────────────────────────────────────────────────────────

    /**
     * RSA 密钥对封装类
     */
    public record RsaKeyPair(String publicKey, String privateKey) {

    }
}