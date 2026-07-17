package com.yzm.fireworks.common.sensitive;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.VersionUtil;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.yzm.fireworks.common.annotation.Sensitive;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * @author JYuan
 * 注册到ObjectMapper的脱敏模块
 */
public class SensitiveModule extends Module {

    private static final String MODULE_NAME = "fireworks-sensitive-module";

    private static final Version VERSION = VersionUtil.parseVersion("1.0.0", "com.yzm.fireworks", MODULE_NAME);

    @Override
    public String getModuleName() {
        return MODULE_NAME;
    }

    @Override
    public Version version() {
        return VERSION;
    }

    @Override
    public void setupModule(SetupContext setupContext) {
        setupContext.addBeanSerializerModifier(new SensitiveBeanSerializerModifier());
    }

    public static class SensitiveBeanSerializerModifier extends BeanSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter writer : beanProperties) {
                if (!ObjectUtils.isEmpty(writer)) {
                    Sensitive sensitive = writer.getAnnotation(Sensitive.class);
                    if (!ObjectUtils.isEmpty(sensitive) && writer.getType().isTypeOrSubTypeOf(String.class)) {
                        // 处理带有@Sensitive注解且字符串类型的属性
                        SensitiveSerializer serializer = new SensitiveSerializer(writer.getSerializer(), sensitive.value());
                        writer.assignSerializer(serializer);
                    }
                }
            }
            return beanProperties;
        }


    }
}
