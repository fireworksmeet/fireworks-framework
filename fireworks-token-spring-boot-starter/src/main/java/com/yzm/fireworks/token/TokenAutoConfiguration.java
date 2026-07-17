package com.yzm.fireworks.token;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Token 自动配置类
 *
 * @author JYuan
 */
@AutoConfiguration
@EnableConfigurationProperties(JWTProperties.class)
public class TokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TokenUtil tokenUtil(JWTProperties jwtProperties) {
        return new TokenUtil(jwtProperties);
    }
}
