package com.yzm.fireworks.token.autoconfigure;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 存在标记为 {@code signing: true} 的密钥时才装配 {@code JwtIssuer}。
 *
 * @author JYuan
 */
final class OnSigningKeyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        TokenProperties properties = Binder.get(context.getEnvironment())
                .bind("fireworks.token", Bindable.of(TokenProperties.class))
                .orElseGet(TokenProperties::new);
        return properties.getKeys().stream().anyMatch(TokenProperties.Key::isSigning);
    }
}
