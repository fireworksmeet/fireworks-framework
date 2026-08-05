package com.yzm.fireworks.common.util.crypto;

import com.yzm.fireworks.common.util.Base64Util;
import com.yzm.fireworks.common.util.crypto.model.RsaKeyPair;
import org.springframework.util.Assert;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA/OAEP 非对称加解密工具类
 *
 * <p>适用于：前端敏感字段加密（如密码登录）、第三方交互短明文加密。
 *
 * @author JYuan
 */
public final class RsaUtil {

    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private static final OAEPParameterSpec OAEP_SHA256_SPEC = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RsaUtil() {
        throw new AssertionError("Utility class");
    }

    /**
     * 生成 RSA 密钥对
     *
     * @param keySize 密钥位长度，推荐 >= 2048
     */
    public static RsaKeyPair generateKeyPair(int keySize) {
        Assert.isTrue(keySize >= 2048, "keySize must be >= 2048");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(keySize, SECURE_RANDOM);
            KeyPair keyPair = generator.generateKeyPair();

            String publicKey = Base64Util.encode(keyPair.getPublic().getEncoded());
            String privateKey = Base64Util.encode(keyPair.getPrivate().getEncoded());

            return new RsaKeyPair(publicKey, privateKey);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key pair generation failed", e);
        }
    }

    /**
     * RSA 公钥加密
     */
    public static String encrypt(String plaintext, String publicKeyBase64) {
        Assert.hasText(plaintext, "plaintext must not be blank");
        Assert.hasText(publicKeyBase64, "publicKey must not be blank");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            PublicKey publicKey = keyFactory.generatePublic(
                    new X509EncodedKeySpec(Base64Util.decodeToBytes(publicKeyBase64)));

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SHA256_SPEC);

            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64Util.encode(cipherBytes);
        } catch (Exception e) {
            throw new IllegalStateException("RSA encrypt failed", e);
        }
    }

    /**
     * RSA 私钥解密
     */
    public static String decrypt(String ciphertextBase64, String privateKeyBase64) {
        Assert.hasText(ciphertextBase64, "ciphertext must not be blank");
        Assert.hasText(privateKeyBase64, "privateKey must not be blank");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            PrivateKey privateKey = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64Util.decodeToBytes(privateKeyBase64)));

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256_SPEC);

            byte[] plainBytes = cipher.doFinal(Base64Util.decodeToBytes(ciphertextBase64));
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("RSA decrypt failed", e);
        }
    }
}