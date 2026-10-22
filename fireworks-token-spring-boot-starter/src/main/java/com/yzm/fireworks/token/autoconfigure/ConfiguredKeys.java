package com.yzm.fireworks.token.autoconfigure;

import com.yzm.fireworks.token.core.TokenAlgorithm;
import com.yzm.fireworks.token.core.TokenConfigurationException;
import com.yzm.fireworks.token.core.key.KeyParser;
import com.yzm.fireworks.token.core.key.SigningKey;
import com.yzm.fireworks.token.core.key.VerificationKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 把配置中的密钥条目转换为签发密钥与验签密钥，并在此处完成全部启动期校验。
 * <p>
 * 每种材料有两个字段：{@code -value}（直接写内容）与 {@code -file}（Spring {@link Resource}），二选一。
 * 用字段名区分来源，因此不需要 {@code file:} / {@code base64:} 之类的前缀语法；
 * 文件是绝对路径还是 classpath 由 Spring 的 {@code Resource} 转换负责。
 *
 * @author JYuan
 */
record ConfiguredKeys(SigningKey signingKey, List<VerificationKey> verificationKeys) {

    static ConfiguredKeys from(TokenProperties properties) {
        List<TokenProperties.Key> configured = properties.getKeys();
        if (configured == null || configured.isEmpty()) {
            throw new TokenConfigurationException("'fireworks.token.keys' must not be empty; "
                    + "add at least one key, or disable the module with 'fireworks.token.enabled=false'");
        }

        long signingCount = configured.stream().filter(TokenProperties.Key::isSigning).count();
        if (signingCount > 1) {
            throw new TokenConfigurationException("only one key may be marked as 'signing: true', but found "
                    + signingCount);
        }

        SigningKey signingKey = null;
        List<VerificationKey> verificationKeys = new ArrayList<>();
        for (TokenProperties.Key key : configured) {
            String kid = key.getKid();
            if (kid == null || kid.isBlank()) {
                throw new TokenConfigurationException("'fireworks.token.keys[].kid' must not be blank");
            }
            TokenAlgorithm algorithm = key.getAlgorithm();
            if (algorithm == null) {
                throw new TokenConfigurationException("key '" + kid + "': 'algorithm' is required");
            }
            assertMaterialCombination(key);

            if (key.isSigning()) {
                signingKey = buildSigningKey(key, algorithm);
            }
            VerificationKey verificationKey = buildVerificationKey(key, algorithm);
            if (verificationKey != null) {
                verificationKeys.add(verificationKey);
            }
        }

        if (signingCount == 1 && signingKey == null) {
            throw new TokenConfigurationException("key marked as 'signing: true' has no private key or secret");
        }
        return new ConfiguredKeys(signingKey, verificationKeys);
    }

    private static void assertMaterialCombination(TokenProperties.Key key) {
        String kid = key.getKid();
        assertOnlyOne(key.getPrivateKeyValue(), key.getPrivateKeyFile(), kid, "private-key");
        assertOnlyOne(key.getPublicKeyValue(), key.getPublicKeyFile(), kid, "public-key");
        assertOnlyOne(key.getSecretValue(), key.getSecretFile(), kid, "secret");

        boolean hasPrivate = hasValue(key.getPrivateKeyValue()) || key.getPrivateKeyFile() != null;
        boolean hasSecret = hasValue(key.getSecretValue()) || key.getSecretFile() != null;
        boolean hasPublic = hasValue(key.getPublicKeyValue()) || key.getPublicKeyFile() != null;

        if (hasPrivate && hasSecret) {
            throw new TokenConfigurationException("key '" + key.getKid()
                    + "': 'private-key' and 'secret' cannot be configured together");
        }
        if (!hasPrivate && !hasSecret && !hasPublic) {
            throw new TokenConfigurationException("key '" + key.getKid()
                    + "': no material configured, set one of '*private-key*', '*public-key*' or '*secret*'");
        }
        if (key.getAlgorithm() != null && key.getAlgorithm().isSymmetric() && hasPrivate) {
            throw new TokenConfigurationException("key '" + key.getKid()
                    + "': symmetric algorithm " + key.getAlgorithm() + " requires a secret, not a private key");
        }
        if (key.getAlgorithm() != null && key.getAlgorithm().isAsymmetric() && hasSecret) {
            throw new TokenConfigurationException("key '" + key.getKid()
                    + "': asymmetric algorithm " + key.getAlgorithm() + " requires a key pair, not a secret");
        }
        if (hasPrivate && !key.isSigning() && !hasPublic) {
            // 没有 signing: true 就不会用于签发；没有公钥又无法本地验签 —— 这把密钥什么都不做
            throw new TokenConfigurationException("key '" + key.getKid() + "': private key is configured but "
                    + "neither 'signing: true' nor a public key is set, so this key can neither sign nor verify");
        }
    }

    private static SigningKey buildSigningKey(TokenProperties.Key key, TokenAlgorithm algorithm) {
        byte[] secret = secretBytes(key);
        if (secret != null) {
            return SigningKey.ofSecret(key.getKid(), algorithm, secret);
        }
        String pem = pem(key.getPrivateKeyValue(), key.getPrivateKeyFile(), key.getKid(), "private-key", false);
        if (pem == null) {
            return null;
        }
        PrivateKey privateKey = parseKey(pem, key.getKid(), KeyParser::parsePrivateKey);
        assertKeyType(key.getKid(), algorithm, privateKey);
        return SigningKey.ofPrivateKey(key.getKid(), algorithm, privateKey);
    }

    private static VerificationKey buildVerificationKey(TokenProperties.Key key, TokenAlgorithm algorithm) {
        byte[] secret = secretBytes(key);
        if (secret != null) {
            return VerificationKey.ofSecret(key.getKid(), algorithm, secret);
        }
        String pem = pem(key.getPublicKeyValue(), key.getPublicKeyFile(), key.getKid(), "public-key", true);
        if (pem == null) {
            // 只有私钥时无法本地验签（EC 私钥不携带公钥点），跳过
            return null;
        }
        PublicKey publicKey = parseKey(pem, key.getKid(), KeyParser::parsePublicKey);
        assertKeyType(key.getKid(), algorithm, publicKey);
        return VerificationKey.ofPublicKey(key.getKid(), algorithm, publicKey);
    }

    /**
     * 算法与密钥类型必须匹配。少了这一步，错误会推迟到签名/验签时变成
     * {@code ClassCastException} 或 Nimbus 的 {@code Unsupported key}，看不出是哪把密钥配错了。
     * <p>
     * 也顺带挡住 Ed25519 这类"能解析但没有对应算法"的密钥：{@code TokenAlgorithm} 里没有 EdDSA。
     */
    private static void assertKeyType(String kid, TokenAlgorithm algorithm, Key key) {
        String expected = switch (algorithm) {
            case RS256 -> "RSA";
            case ES256 -> "EC (P-256)";
            case HS256 -> "secret";
        };
        boolean matches = switch (algorithm) {
            case RS256 -> key instanceof RSAPrivateKey || key instanceof RSAPublicKey;
            case ES256 -> (key instanceof ECPrivateKey priv && isP256(priv))
                    || (key instanceof ECPublicKey pub && isP256(pub));
            case HS256 -> false;
        };
        if (!matches) {
            String note = key.getAlgorithm().startsWith("Ed") ? " (Ed25519/EdDSA is not supported)" : "";
            throw new TokenConfigurationException("key '" + kid + "': algorithm " + algorithm + " requires "
                    + expected + " key material, but got " + key.getAlgorithm() + note);
        }
    }

    private static boolean isP256(java.security.interfaces.ECKey key) {
        return key.getParams().getCurve().getField().getFieldSize() == 256;
    }

    /**
     * {@code -value} 与 {@code -file} 二选一；都没有返回 {@code null}。
     */
    private static String pem(String value, Resource file, String kid, String field, boolean allowClasspath) {
        if (hasValue(value)) {
            return value;
        }
        return file == null ? null : readText(file, kid, field, allowClasspath);
    }

    private static byte[] secretBytes(TokenProperties.Key key) {
        if (hasValue(key.getSecretValue())) {
            return key.getSecretValue().strip().getBytes(StandardCharsets.UTF_8);
        }
        return key.getSecretFile() == null ? null : stripAscii(readBytes(key.getSecretFile(), key.getKid()));
    }

    private static void assertOnlyOne(String value, Resource file, String kid, String field) {
        if (hasValue(value) && file != null) {
            throw new TokenConfigurationException("key '" + kid + "': set only one of '" + field
                    + "-value' / '" + field + "-file'");
        }
    }

    private static String readText(Resource resource, String kid, String field, boolean allowClasspath) {
        assertNotClasspath(resource, kid, field, allowClasspath);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TokenConfigurationException("key '" + kid + "': unable to read '" + field + "-file' from "
                    + resource, e);
        }
    }

    private static byte[] readBytes(Resource resource, String kid) {
        assertNotClasspath(resource, kid, "secret", false);
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new TokenConfigurationException("key '" + kid + "': unable to read 'secret-file' from "
                    + resource, e);
        }
    }

    /**
     * 私钥与对称密钥不允许从 classpath 加载：打进 jar 会随制品进入镜像仓库与 CI 缓存。
     */
    private static void assertNotClasspath(Resource resource, String kid, String field, boolean allowClasspath) {
        if (!allowClasspath && resource instanceof ClassPathResource) {
            throw new TokenConfigurationException("key '" + kid + "': '" + field
                    + "-file' must not be loaded from classpath, put the key outside the jar");
        }
    }

    /**
     * 文件里常见 editors / echo 追加的首尾换行；对称密钥按字节比对，必须去掉。
     */
    private static byte[] stripAscii(byte[] bytes) {
        int start = 0;
        int end = bytes.length;
        while (start < end && bytes[start] <= ' ') {
            start++;
        }
        while (end > start && bytes[end - 1] <= ' ') {
            end--;
        }
        byte[] result = new byte[end - start];
        System.arraycopy(bytes, start, result, 0, result.length);
        return result;
    }

    private static boolean hasValue(String value) {
        return StringUtils.hasText(value);
    }

    /**
     * 解析失败时统一补上 {@code key 'kid': } 前缀，便于一眼定位是哪把密钥。
     */
    private static <K> K parseKey(String pem, String kid, Function<String, K> parser) {
        try {
            return parser.apply(pem);
        } catch (TokenConfigurationException e) {
            throw new TokenConfigurationException("key '" + kid + "': " + e.getMessage(), e);
        }
    }
}
