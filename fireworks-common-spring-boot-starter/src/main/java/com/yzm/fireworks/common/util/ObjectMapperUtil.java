package com.yzm.fireworks.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * @author JYuan
 */
public class ObjectMapperUtil {

    public static ObjectMapper getObjectMapper() {
        return ApplicationContextUtil.getBean(ObjectMapper.class);
    }

    public static <T> T deserialize(String json, Class<T> clazz) {
        try {
            return getObjectMapper().readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T, D> T deserialize(String json, Class<T> hasGeneric, Class<D> element) {
        ObjectMapper objectMapper = getObjectMapper();
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(hasGeneric, element);
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try {
            return getObjectMapper().readValue(bytes, clazz);
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T, D> T deserialize(byte[] bytes, Class<T> hasGeneric, Class<D> element) {
        ObjectMapper objectMapper = getObjectMapper();
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(hasGeneric, element);
        try {
            return objectMapper.readValue(bytes, javaType);
        } catch (IOException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static String stringify(Object value) {
        try {
            return getObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        return getObjectMapper().convertValue(fromValue, toValueType);
    }

    public static byte[] serialize(Object value) {
        try {
            return getObjectMapper().writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
