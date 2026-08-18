package com.yzm.fireworks.common;

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
import com.yzm.fireworks.common.decorator.MdcTaskDecorator;
import com.yzm.fireworks.common.sensitive.SensitiveModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 通用基础自动配置。
 * <p>
 * 提供 MDC 上下文传递、Jackson 全局序列化定制（宽松解析、BigDecimal 字符串化、脱敏模块）等 common 基础设施。
 * <p>
 * 说明：时间类型（LocalDateTime / Instant / OffsetDateTime 等）不在此处手动配置，统一交由
 * JavaTimeModule 默认实现按 ISO 格式序列化，且不设置全局 TimeZone，避免给无时区类型附加时区语义。
 *
 * @author JYuan
 */
@AutoConfiguration
public class CommonAutoConfiguration {

    /**
     * 注册 MDC / Tracing 上下文传递装饰器，供异步线程池使用。
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /**
     * 注册 Jackson 全局定制器。
     * <p>
     * Spring Boot 会收集容器中所有 {@link Jackson2ObjectMapperBuilderCustomizer} 类型的 Bean
     * 并统一应用到 ObjectMapper，因此这里直接返回标准接口即可。
     */
    @Bean
    @ConditionalOnClass(Jackson2ObjectMapperBuilderCustomizer.class)
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            // 基础配置
            builder
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
                    // 该特性决定parser是否允许JSON字符串包含非引号控制字符（值小于32的ASCII字符，包含制表符和换行符）。
                    // 如果该属性关闭，则如果遇到这些字符，则会抛出异常。JSON标准说明书要求所有控制符必须使用引号，
                    // 因此这是一个非标准的特性
                    .featuresToEnable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                    // 允许反斜杠转义任意字符
                    .featuresToEnable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature())
                    // 只序列化不为空的字段
                    .serializationInclusion(JsonInclude.Include.NON_NULL);

            // ============================================================
            // 不再手动配置：
            //
            // LocalDate
            // LocalTime
            // LocalDateTime
            // Instant
            //
            // 统一使用 Jackson JavaTimeModule 默认实现。
            //
            // 同时不设置：
            //
            // TimeZone
            //
            // 避免人为给没有时区的时间类型附加全局时区语义。
            // ============================================================

            // 配置 BigDecimal / BigInteger 以普通字符串输出，防止科学计数法和 JS 精度丢失
            SimpleModule simpleModule = new SimpleModule()
                    .addSerializer(BigInteger.class, ToStringSerializer.instance)
                    .addSerializer(BigDecimal.class, new JsonSerializer<>() {
                        @Override
                        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                            if (ObjectUtils.isEmpty(value)) {
                                gen.writeNull();
                            } else {
                                gen.writeString(value.toPlainString());
                            }
                        }
                    });

            // 配置自定义的脱敏模块
            builder.modules(new SensitiveModule(), simpleModule);
        };
    }
}
