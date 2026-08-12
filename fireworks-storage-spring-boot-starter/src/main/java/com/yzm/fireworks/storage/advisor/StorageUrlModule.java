package com.yzm.fireworks.storage.advisor;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.VersionUtil;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.yzm.fireworks.common.annotation.StorageUrl;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * 对象存储 URL 序列化模块。
 *
 * <p>将标注了 {@code @StorageUrl} 的字段动态绑定到 {@link StorageUrlJsonSerializer}，
 * 实现「注解声明于 common、URL 解析逻辑位于 storage」的解耦绑定，
 * 避免 common 反向依赖 storage 造成循环依赖。</p>
 *
 * @author JYuan
 */
public class StorageUrlModule extends Module {

    private static final String MODULE_NAME = "fireworks-storage-url-module";

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
        setupContext.addBeanSerializerModifier(new StorageUrlBeanSerializerModifier());
    }

    /**
     * 遍历 Bean 字段，为标注 {@code @StorageUrl} 的字段绑定 URL 序列化器
     */
    public static class StorageUrlBeanSerializerModifier extends BeanSerializerModifier {

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                                                         List<BeanPropertyWriter> beanProperties) {
            for (BeanPropertyWriter writer : beanProperties) {
                if (ObjectUtils.isEmpty(writer)) {
                    continue;
                }
                StorageUrl annotation = writer.getAnnotation(StorageUrl.class);
                if (!ObjectUtils.isEmpty(annotation)) {
                    writer.assignSerializer(new StorageUrlJsonSerializer(
                            annotation.source(),
                            annotation.bucket(),
                            annotation.type(),
                            annotation.durationSeconds(),
                            annotation.delimiter()
                    ));
                }
            }
            return beanProperties;
        }
    }
}
