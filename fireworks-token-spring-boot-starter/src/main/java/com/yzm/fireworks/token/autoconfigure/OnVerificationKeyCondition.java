package com.yzm.fireworks.token.autoconfigure;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 存在可用于验签的材料（公钥或对称密钥）时才装配 {@code JwtVerifier}。
 *
 * @author JYuan
 */
final class OnVerificationKeyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        TokenProperties properties = Binder.get(context.getEnvironment())
                .bind("fireworks.token", Bindable.of(TokenProperties.class))
                .orElseGet(TokenProperties::new);
        return properties.getKeys().stream().anyMatch(key -> StringUtils.hasText(key.getPublicKeyValue())
                || key.getPublicKeyFile() != null
                || StringUtils.hasText(key.getSecretValue())
                || key.getSecretFile() != null);
    }
}
