package com.yzm.fireworks.storage.advisor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.yzm.fireworks.common.annotation.StorageUrl;
import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.common.enums.UrlType;
import com.yzm.fireworks.common.util.SpringContextHolder;
import com.yzm.fireworks.storage.service.StorageService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor
@AllArgsConstructor
public class StorageUrlJsonSerializer extends JsonSerializer<Object> implements ContextualSerializer {

    private String source;
    private String bucket;
    private UrlType type;
    private long durationSeconds;
    private String delimiter;

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property == null) {
            return prov.findNullValueSerializer(null);
        }

        StorageUrl annotation = property.getAnnotation(StorageUrl.class);
        if (annotation != null) {
            return new StorageUrlJsonSerializer(
                    annotation.source(),
                    annotation.bucket(),
                    annotation.type(),
                    annotation.durationSeconds(),
                    annotation.delimiter()
            );
        }
        return prov.findValueSerializer(property.getType(), property);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        StorageService storageService = SpringContextHolder.getBean(StorageService.class);
        if (storageService == null) {
            gen.writeObject(value);
            return;
        }

        Object targetKeyObj = value;

        // 场景 1：如果配置了 source 关联字段，从当前序列化对象中读取源 ObjectKey
        if (StringUtils.hasText(source)) {
            Object currentValue = gen.currentValue();
            if (currentValue != null) {
                Field sourceField = ReflectionUtils.findField(currentValue.getClass(), source);
                if (sourceField != null) {
                    ReflectionUtils.makeAccessible(sourceField);
                    targetKeyObj = ReflectionUtils.getField(sourceField, currentValue);
                }
            }
        }

        if (targetKeyObj == null) {
            gen.writeNull();
            return;
        }

        Duration duration = Duration.ofSeconds(durationSeconds);

        // 场景 2：多态解析（集合 / 数组 / 分隔符字符串 / 单个 Key）
        if (targetKeyObj instanceof Collection<?> collection) {
            List<String> urls = collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .map(key -> resolveUrl(storageService, key, bucket, type, duration))
                    .toList();
            gen.writeObject(urls);

        } else if (targetKeyObj.getClass().isArray()) {
            Object[] arr = (Object[]) targetKeyObj;
            List<String> urls = Arrays.stream(arr)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .map(key -> resolveUrl(storageService, key, bucket, type, duration))
                    .toList();
            gen.writeObject(urls);

        } else if (targetKeyObj instanceof String keyStr) {
            if (!StringUtils.hasText(keyStr)) {
                gen.writeString(StringPool.EMPTY);
                return;
            }

            if (keyStr.contains(delimiter)) {
                List<String> urls = Arrays.stream(keyStr.split(delimiter))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(key -> resolveUrl(storageService, key, bucket, type, duration))
                        .toList();

                // 如果目标字段是 List 类型，直接输出 JSON Array，否则输出分隔符拼接串
                if (value instanceof Collection) {
                    gen.writeObject(urls);
                } else {
                    gen.writeString(String.join(delimiter, urls));
                }
            } else {
                gen.writeString(resolveUrl(storageService, keyStr.trim(), bucket, type, duration));
            }
        } else {
            gen.writeObject(value);
        }
    }

    private String resolveUrl(StorageService storageService, String key, String bucket, UrlType type, Duration duration) {
        return switch (type) {
            case PUBLIC -> storageService.getPublicUrl(bucket, key);
            case PRESIGNED -> storageService.getPresignedUrl(bucket, key, duration);
            case GATEWAY -> storageService.getGatewayUrl(bucket, key, duration);
        };
    }
}