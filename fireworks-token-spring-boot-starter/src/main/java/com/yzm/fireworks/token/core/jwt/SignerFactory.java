package com.yzm.fireworks.token.core.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.yzm.fireworks.token.core.TokenConfigurationException;
import com.yzm.fireworks.token.core.key.SigningKey;
import com.yzm.fireworks.token.core.key.VerificationKey;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

/**
 * 按算法构造 Nimbus 的签名器与校验器。
 * <p>
 * 所有 Nimbus 类型都收敛在本包内，契约层只看到 {@link SigningKey} / {@link VerificationKey}。
 *
 * @author JYuan
 */
final class SignerFactory {

    private SignerFactory() {
    }

    static JWSSigner signer(SigningKey key) {
        try {
            return switch (key.algorithm()) {
                case HS256 -> new MACSigner(key.secret());
                case RS256 -> new RSASSASigner(key.privateKey());
                case ES256 -> new ECDSASigner((ECPrivateKey) key.privateKey());
            };
        } catch (JOSEException e) {
            throw new TokenConfigurationException("unable to initialize signer for key '" + key.keyId()
                    + "' with algorithm " + key.algorithm(), e);
        }
    }

    static JWSVerifier verifier(VerificationKey key) {
        try {
            return switch (key.algorithm()) {
                case HS256 -> new MACVerifier(key.secret());
                case RS256 -> new RSASSAVerifier((RSAPublicKey) key.publicKey());
                case ES256 -> new ECDSAVerifier((ECPublicKey) key.publicKey());
            };
        } catch (JOSEException | ClassCastException e) {
            throw new TokenConfigurationException("unable to initialize verifier for key '" + key.keyId()
                    + "' with algorithm " + key.algorithm()
                    + "': key material does not match the algorithm", e);
        }
    }
}
