package com.yzm.fireworks.common.util.crypto;

import com.yzm.fireworks.common.util.Base64Util;
import lombok.experimental.UtilityClass;
import org.springframework.util.Assert;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * AES-GCM 对称加解密工具类
 *
 * <p>特性说明：
 * <ul>
 *   <li>变换算法：{@code AES/GCM/NoPadding} (同时提供保密性与完整性校验)</li>
 *   <li>随机 IV：每次加密前自动生成 12 字节动态 IV，拼接在密文头部</li>
 * </ul>
 *
 * @author JYuan
 */
@UtilityClass
public class AesUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // GCM 推荐标准 IV 长度
    private static final int GCM_TAG_LENGTH = 128; // GCM 认证标签长度 (bits)
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成 AES 对称密钥 (Base64 编码)
     *
     * @param keySize 密钥位长度，推荐 128 或 256
     * @return Base64 编码的密钥字符串
     */
    public static String generateKey(int keySize) {
        Assert.isTrue(keySize == 128 || keySize == 192 || keySize == 256, "keySize must be 128, 192, or 256");
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(keySize, SECURE_RANDOM);
            SecretKey secretKey = keyGen.generateKey();
            return Base64Util.encode(secretKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("AES key generation failed", e);
        }
    }

    /**
     * AES-GCM 加密
     *
     * @param plaintext 明文
     * @param secretKeyBase64 Base64 编码的密钥字符串
     * @return Base64 编码的密文（包含 12 字节 IV 头）
     */
    public static String encrypt(String plaintext, String secretKeyBase64) {
        Assert.hasText(plaintext, "plaintext must not be blank");
        Assert.hasText(secretKeyBase64, "secretKey must not be blank");
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(secretKeyBase64);
            SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 组合 IV 与 密文 (IV + CipherText)
            byte[] combined = new byte[GCM_IV_LENGTH + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherBytes, 0, combined, GCM_IV_LENGTH, cipherBytes.length);

            return Base64Util.encode(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES encrypt failed", e);
        }
    }

    /**
     * AES-GCM 解密
     *
     * @param ciphertextBase64 Base64 编码的密文（前 12 字节为 IV）
     * @param secretKeyBase64 Base64 编码的密钥字符串
     * @return 解密后的明文
     */
    public static String decrypt(String ciphertextBase64, String secretKeyBase64) {
        Assert.hasText(ciphertextBase64, "ciphertext must not be blank");
        Assert.hasText(secretKeyBase64, "secretKey must not be blank");
        try {
            byte[] combined = Base64Util.decodeToBytes(ciphertextBase64);
            Assert.isTrue(combined.length > GCM_IV_LENGTH, "invalid ciphertext length");

            byte[] keyBytes = Base64Util.decodeToBytes(secretKeyBase64);
            SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, GCM_IV_LENGTH));

            byte[] plainBytes = cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES decrypt failed or data tampered", e);
        }
    }
}