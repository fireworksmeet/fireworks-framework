package com.yzm.fireworks.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 序列化与反序列化工具类
 *
 * @author JYuan
 */
@UtilityClass
public class JsonUtil {

    public static ObjectMapper getObjectMapper() {
        return SpringContextHolder.getBean(ObjectMapper.class);
    }

    // ==================== 1. 序列化 (Object -> String / byte[]) ====================

    /**
     * 将对象转为 JSON 字符串
     */
    public static String stringify(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return getObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // 不拼接对象内容：toString 可能带敏感字段，异常消息会进日志
            throw new IllegalArgumentException("JSON stringify error, type=" + value.getClass().getName(), e);
        }
    }

    /**
     * 将对象转为字节数组
     */
    public static byte[] serialize(Object value) {
        if (value == null) {
            return new byte[0];
        }
        try {
            return getObjectMapper().writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON serialize error, type=" + value.getClass().getName(), e);
        }
    }

    // ==================== 2. 通用反序列化 (String / byte[] -> Object) ====================

    /**
     * 普通对象反序列化 (例如: String -> User)
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        if (isEmpty(json)) {
            return null;
        }
        try {
            return getObjectMapper().readValue(json, clazz);
        } catch (JsonProcessingException e) {
            // 不拼接原始报文：内容可能来自外部输入，进异常消息会连带 PII 进日志
            throw new IllegalArgumentException("JSON deserialize error: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * 万能泛型反序列化 (例如: Result<User>, Result<List<User>>, Map<String, User> 等)
     */
    public static <T> T deserialize(String json, TypeReference<T> typeReference) {
        if (isEmpty(json)) {
            return null;
        }
        try {
            return getObjectMapper().readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON deserialize error: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * 字节数组转普通对象
     */
    public static <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return getObjectMapper().readValue(bytes, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("Bytes deserialize error", e);
        }
    }

    /**
     * 字节数组转复杂泛型对象
     */
    public static <T> T deserialize(byte[] bytes, TypeReference<T> typeReference) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return getObjectMapper().readValue(bytes, typeReference);
        } catch (IOException e) {
            throw new IllegalArgumentException("Bytes deserialize error", e);
        }
    }

    // ==================== 3. 常用高频快捷转换方法 ====================

    /**
     * 快捷转换：JSON 字符串 -> Map<String, Object>
     */
    public static Map<String, Object> toMap(String json) {
        if (isEmpty(json)) {
            return Collections.emptyMap();
        }
        return deserialize(json, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 快捷转换：JSON 字符串 -> 指定类型的 Map<K, V>
     */
    public static <K, V> Map<K, V> toMap(String json, Class<K> keyClass, Class<V> valueClass) {
        if (isEmpty(json)) {
            return Collections.emptyMap();
        }
        ObjectMapper objectMapper = getObjectMapper();
        JavaType javaType = objectMapper.getTypeFactory().constructMapType(Map.class, keyClass, valueClass);
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON to Map error: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * 快捷转换：JSON 字符串 -> List<T>
     */
    public static <T> List<T> toList(String json, Class<T> elementClass) {
        if (isEmpty(json)) {
            return Collections.emptyList();
        }
        ObjectMapper objectMapper = getObjectMapper();
        JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass);
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON to List error: " + e.getOriginalMessage(), e);
        }
    }

    // ==================== 4. 对象映射与转换 ====================

    /**
     * POJO/Map 间的深拷贝与类型转换 (基于 Jackson 内部 mapping)
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        if (fromValue == null) {
            return null;
        }
        return getObjectMapper().convertValue(fromValue, toValueType);
    }

    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}