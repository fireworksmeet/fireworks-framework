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
    private SensitiveType[] sensitiveTypes;

    @Override
    public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        if (o instanceof String str) {
            if (!ObjectUtils.isEmpty(sensitiveTypes)) {
                try {
                    o = processWithTypes(str);
                } catch (Exception e) {
                    // 脱敏异常时保留原值并记录日志，避免因脱敏失败导致整个序列化中断
                    log.warn("[Sensitive] 脱敏处理异常，字段将保留原值。sensitiveTypes={}", sensitiveTypes, e);
                }
            } else {
                log.warn("[Sensitive] sensitiveTypes 为空，字段不会被脱敏，请检查 @Sensitive 注解配置");
            }
        }
        if (ObjectUtils.isEmpty(serializer)) {
            serializerProvider.defaultSerializeValue(o, jsonGenerator);
        } else {
            serializer.serialize(o, jsonGenerator, serializerProvider);
        }
    }

    /**
     * 根据脱敏类型数组处理字符串
     * 遍历数组，找到第一个 match() 匹配的类型进行脱敏；若无匹配则使用第一个类型
     */
    private String processWithTypes(String str) {
        // 优先使用匹配的类型
        for (SensitiveType type : sensitiveTypes) {
            if (type.match(str)) {
                return type.process(str);
            }
        }
        // 无匹配时使用第一个类型作为默认处理
        return sensitiveTypes[0].process(str);
    }
}