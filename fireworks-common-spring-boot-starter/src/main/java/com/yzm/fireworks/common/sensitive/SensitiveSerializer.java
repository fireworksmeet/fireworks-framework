package com.yzm.fireworks.common.sensitive;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yzm.fireworks.common.enums.SensitiveType;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.io.IOException;

/**
 * @author JYuan
 * 脱敏时用的序列化器
 */
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class SensitiveSerializer extends JsonSerializer<Object> {

    private JsonSerializer<Object> serializer;
    private SensitiveType sensitiveType;

    @Override
    public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        if (o instanceof String str && sensitiveType != null) {
            try {
                o = sensitiveType.process(str);
            } catch (Exception e) {
                // 脱敏异常时保留原值并记录日志，避免因脱敏失败导致整个序列化中断
                log.warn("[Sensitive] 脱敏处理异常，字段将保留原值。sensitiveType={}", sensitiveType, e);
            }
        }
        if (ObjectUtils.isEmpty(serializer)) {
            serializerProvider.defaultSerializeValue(o, jsonGenerator);
        } else {
            serializer.serialize(o, jsonGenerator, serializerProvider);
        }
    }
}
