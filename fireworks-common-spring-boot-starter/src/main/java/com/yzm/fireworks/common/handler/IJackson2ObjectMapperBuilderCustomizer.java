package com.yzm.fireworks.common.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.yzm.fireworks.common.sensitive.SensitiveModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import static com.yzm.fireworks.common.util.TimeUtil.*;


/**
 * ObjectMapper的定制器
 * 如果向容器中注入了这个定制器，它会被Spring Boot的自动配置使用，在创建ObjectMapper的时候使用
 *
 * @author JYuan
 */
public class IJackson2ObjectMapperBuilderCustomizer implements Jackson2ObjectMapperBuilderCustomizer {
    @Override
    public void customize(Jackson2ObjectMapperBuilder builder) {
        // 基础配置
        builder.simpleDateFormat(DEFAULT_DATE_TIME_FORMAT)
                .timeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                // 禁用时间戳格式
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 允许反序列化时，忽略未知字段
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 空对象不抛出异常
                .featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // 允许单引号
                .featuresToEnable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
                // 允许非双引号属性名
                .featuresToEnable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                // 该特性决定parser是否允许JSON字符串包含非引号控制字符（值小于32的ASCII字符，包含制表符和换行符）。 如果该属性关闭，则如果遇到这些字符，则会抛出异常。JSON标准说明书要求所有控制符必须使用引号，因此这是一个非标准的特性
                .featuresToEnable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                // 允许反斜杠转义任意字符
                .featuresToEnable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature())
                // 只序列化不为空的字段
                .serializationInclusion(JsonInclude.Include.NON_NULL);

        // Java8时间类配置
        builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)))
                .serializerByType(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)))
                .serializerByType(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)))
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)))
                .deserializerByType(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)));

        // 配置BigDecimal
        SimpleModule simpleModule = new SimpleModule()
                .addSerializer(BigInteger.class, ToStringSerializer.instance)
                .addSerializer(BigDecimal.class, new JsonSerializer<>() {
                    @Override
                    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                        if (ObjectUtils.isEmpty(value)) {
                            gen.writeNull();
                        } else {
                            // 强制以普通字符串形式输出，防止科学计数法和JS精度丢失
                            gen.writeString(value.toPlainString());
                        }
                    }
                });

        // 配置自定义的脱敏模块
        builder.modules(new SensitiveModule(), simpleModule);
    }
}
