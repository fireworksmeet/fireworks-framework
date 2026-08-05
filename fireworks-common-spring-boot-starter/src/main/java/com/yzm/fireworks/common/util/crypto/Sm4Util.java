package com.yzm.fireworks.common.util.crypto;

import com.yzm.fireworks.common.util.Base64Util;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.util.Assert;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;

/**
 * 国密 SM4 对称加解密工具类
 *
 * <p>变换算法：{@code SM4/CBC/PKCS7Padding}，密钥长度固定 128 位 (16 字节)。
 *
 * @author JYuan
 */
public final class Sm4Util {

    private static final String PROVIDER_NAME = "BC";
    private static final String ALGORITHM = "SM4";
    private static final String TRANSFORMATION = "SM4/CBC/PKCS7Padding";
    private static final int IV_LENGTH = 16; // SM4 分组块大小为 16 字节
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Util() {
        throw new AssertionError("Utility class");
    }

    /**
     * 生成 SM4 对称密钥 (Base64 编码，固定 16 字节)
     */
    public static String generateKey() {
        byte[] keyBytes = new byte[16];
        SECURE_RANDOM.nextBytes(keyBytes);
        return Base64Util.encode(keyBytes);
    }

    /**
     * SM4 加密
     */
    public static String encrypt(String plaintext, String secretKeyBase64) {
        Assert.hasText(plaintext, "plaintext must not be blank");
        Assert.hasText(secretKeyBase64, "secretKey must not be blank");
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(secretKeyBase64);
            Assert.isTrue(keyBytes.length == 16, "SM4 key length must be 16 bytes");

            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER_NAME);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(cipherBytes, 0, combined, IV_LENGTH, cipherBytes.length);

            return Base64Util.encode(combined);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 encrypt failed", e);
        }
    }

    /**
     * SM4 解密
     */
    public static String decrypt(String ciphertextBase64, String secretKeyBase64) {
        Assert.hasText(ciphertextBase64, "ciphertext must not be blank");
        Assert.hasText(secretKeyBase64, "secretKey must not be blank");
        try {
            byte[] combined = Base64Util.decodeToBytes(ciphertextBase64);
            Assert.isTrue(combined.length > IV_LENGTH, "invalid ciphertext length");

            byte[] keyBytes = Base64Util.decodeToBytes(secretKeyBase64);
            Assert.isTrue(keyBytes.length == 16, "SM4 key length must be 16 bytes");

            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherBytes = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER_NAME);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 decrypt failed", e);
        }
    }
}