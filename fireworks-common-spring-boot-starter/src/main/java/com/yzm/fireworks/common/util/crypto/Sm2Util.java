package com.yzm.fireworks.common.util.crypto;

import com.yzm.fireworks.common.util.Base64Util;
import com.yzm.fireworks.common.util.crypto.model.Sm2KeyPair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.util.Assert;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 国密 SM2 非对称加解密工具类
 *
 * @author JYuan
 */
public final class Sm2Util {

    private static final String PROVIDER_NAME = "BC";
    private static final String ALGORITHM = "EC";
    private static final String SM2_CURVE = "sm2p256v1";
    private static final String TRANSFORMATION = "SM2";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm2Util() {
        throw new AssertionError("Utility class");
    }

    /**
     * 生成 SM2 密钥对
     */
    public static Sm2KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER_NAME);
            generator.initialize(new ECGenParameterSpec(SM2_CURVE), SECURE_RANDOM);
            KeyPair keyPair = generator.generateKeyPair();

            String publicKey = Base64Util.encode(keyPair.getPublic().getEncoded());
            String privateKey = Base64Util.encode(keyPair.getPrivate().getEncoded());

            return new Sm2KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 key pair generation failed", e);
        }
    }

    /**
     * SM2 公钥加密
     */
    public static String encrypt(String plaintext, String publicKeyBase64) {
        Assert.hasText(plaintext, "plaintext must not be blank");
        Assert.hasText(publicKeyBase64, "publicKey must not be blank");
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(publicKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER_NAME);
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64Util.encode(cipherBytes);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 encrypt failed", e);
        }
    }

    /**
     * SM2 私钥解密
     */
    public static String decrypt(String ciphertextBase64, String privateKeyBase64) {
        Assert.hasText(ciphertextBase64, "ciphertext must not be blank");
        Assert.hasText(privateKeyBase64, "privateKey must not be blank");
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(privateKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER_NAME);
            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] plainBytes = cipher.doFinal(Base64Util.decodeToBytes(ciphertextBase64));
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 decrypt failed", e);
        }
    }
}