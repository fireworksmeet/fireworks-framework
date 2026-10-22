package com.yzm.fireworks.token.core.key;

import com.yzm.fireworks.token.core.TokenConfigurationException;
import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

import java.io.IOException;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * PEM 解析：把密钥文件里的文本变成 JDK 密钥。
 * <p>
 * 解析本身交给 BouncyCastle 的 {@link PEMParser}（PKCS#1 / SEC1 / PKCS#8 / X.509 / 证书都由它识别），
 * 本类只做三件它不做的事：
 * <ul>
 *     <li>把"解析出来的对象类型"翻译成明确的失败原因（加密私钥、证书当私钥用、多段 PEM）</li>
 *     <li>对不支持的格式给出可直接执行的转换命令，而不是 {@code PEMException}</li>
 *     <li>统一异常类型为 {@link TokenConfigurationException}，由启动期校验统一收集</li>
 * </ul>
 *
 * @author JYuan
 */
@UtilityClass
public class KeyParser {

    /**
     * 解析私钥：PKCS#8（{@code BEGIN PRIVATE KEY}）、PKCS#1（{@code BEGIN RSA PRIVATE KEY}）、
     * SEC1（{@code BEGIN EC PRIVATE KEY}）。
     */
    public static PrivateKey parsePrivateKey(String pem) {
        Object object = readSingleBlock(requireText(pem, "private-key"), "private-key");
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        try {
            if (object instanceof PEMKeyPair keyPair) {
                // SEC1（EC PRIVATE KEY）把曲线参数放在私钥结构里，PKCS#8 的算法标识中没有，
                // BouncyCastle 无法直接转换；RSA 的 PKCS#1 带完整公私钥信息，可以走 getKeyPair。
                if (keyPair.getPublicKeyInfo() == null) {
                    throw new TokenConfigurationException("SEC1 (EC PRIVATE KEY) is not supported, convert it to "
                            + "PKCS#8: openssl pkcs8 -topk8 -nocrypt -in ec-key.pem -out ec-key-pkcs8.pem");
                }
                return converter.getKeyPair(keyPair).getPrivate();
            }
            if (object instanceof PrivateKeyInfo keyInfo) {
                return converter.getPrivateKey(keyInfo);
            }
        } catch (Exception e) {
            throw new TokenConfigurationException("unable to parse 'private-key': " + e.getMessage(), e);
        }
        throw unsupportedPrivateKey(object);
    }

    /**
     * 解析公钥：X.509（{@code BEGIN PUBLIC KEY}）或证书（{@code BEGIN CERTIFICATE}）。
     */
    public static PublicKey parsePublicKey(String pem) {
        Object object = readSingleBlock(requireText(pem, "public-key"), "public-key");
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        try {
            switch (object) {
                case SubjectPublicKeyInfo keyInfo -> {
                    return converter.getPublicKey(keyInfo);
                }
                case X509CertificateHolder certificate -> {
                    return converter.getPublicKey(certificate.getSubjectPublicKeyInfo());
                }
                case PEMKeyPair keyPair when keyPair.getPublicKeyInfo() != null -> {
                    return converter.getPublicKey(keyPair.getPublicKeyInfo());
                }
                default -> {
                }
            }
        } catch (Exception e) {
            throw new TokenConfigurationException("unable to parse 'public-key': " + e.getMessage(), e);
        }
        throw new TokenConfigurationException("'public-key' must be an X.509 public key or certificate PEM, but was "
                + object.getClass().getSimpleName());
    }

    private static String requireText(String pem, String field) {
        if (pem == null || pem.isBlank()) {
            throw new TokenConfigurationException("'" + field + "' must not be blank");
        }
        return pem.trim();
    }

    /**
     * 只读取第一段 PEM；多段（如证书链）直接拒绝，避免"到底用了哪张证书"的歧义。
     */
    private static Object readSingleBlock(String text, String field) {
        try (PEMParser parser = new PEMParser(new StringReader(text))) {
            Object first = parser.readObject();
            if (first == null) {
                throw new TokenConfigurationException("'" + field + "' is not a PEM block; for a DER file convert it "
                        + "first: openssl pkey -inform DER -in key.der -out key.pem");
            }
            if (parser.readObject() != null) {
                throw new TokenConfigurationException("'" + field + "' contains more than one PEM block; provide a "
                        + "single key (certificate chains are not supported)");
            }
            return first;
        } catch (IOException e) {
            throw new TokenConfigurationException(withHint(text, "unable to parse '" + field + "': " + e.getMessage()), e);
        }
    }

    /**
     * 解析失败时补上最常见的格式问题；BouncyCastle 的异常信息只有 {@code unrecognised object}。
     */
    private static String withHint(String text, String message) {
        if (text.contains("OPENSSH PRIVATE KEY")) {
            return message + "; OpenSSH private keys must be exported as PEM first"
                    + " (RSA/ECDSA: ssh-keygen -p -m PEM -f id_rsa)."
                    + " Ed25519 (id_ed25519) cannot be converted and is not supported as a JWT signing key";
        }
        if (text.contains("ENCRYPTED PRIVATE KEY")) {
            return message + "; decrypt it first: openssl pkcs8 -in encrypted.pem -out key.pem";
        }
        return message;
    }

    private static TokenConfigurationException unsupportedPrivateKey(Object object) {
        if (object instanceof PKCS8EncryptedPrivateKeyInfo) {
            return new TokenConfigurationException("encrypted private key is not supported, decrypt it first: "
                    + "openssl pkcs8 -in encrypted.pem -out key.pem");
        }
        if (object instanceof X509CertificateHolder) {
            return new TokenConfigurationException("'private-key' is a certificate, which only carries a public key; "
                    + "put it in 'public-key-value' instead");
        }
        if (object instanceof SubjectPublicKeyInfo) {
            return new TokenConfigurationException("'private-key' is a public key; put it in 'public-key-value' instead");
        }
        return new TokenConfigurationException("'private-key' must be a PKCS#8 / PKCS#1 / SEC1 private key PEM, but was "
                + object.getClass().getSimpleName());
    }
}
