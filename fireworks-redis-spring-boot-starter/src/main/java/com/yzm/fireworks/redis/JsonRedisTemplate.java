package com.yzm.fireworks.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * @author JYuan
 */
@Getter
public class JsonRedisTemplate extends RedisTemplate<String, Object> {

    private final ObjectMapper objectMapper;
    private final RedisSerializer<String> keySerializer;
    private final Jackson2JsonRedisSerializer<Object> valueSerializer;

    public JsonRedisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        keySerializer = RedisSerializer.string();
        valueSerializer = new Jackson2JsonRedisSerializer<>(this.objectMapper, Object.class);
        this.setKeySerializer(keySerializer);
        this.setValueSerializer(valueSerializer);
        this.setHashKeySerializer(keySerializer);
        this.setHashValueSerializer(valueSerializer);
        this.setConnectionFactory(connectionFactory);
        this.afterPropertiesSet();
    }
}