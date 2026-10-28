package com.yzm.fireworks.api.enums;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 对外暴露枚举的扫描配置属性
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.exposed-enum")
public class ExposedEnumScanProperties {

    /**
     * 扫描包路径列表（可选）
     * <p>
     * 不配置时取启动类（{@code @SpringBootApplication}）所在包；需要扫描启动类包之外的位置时才显式指定：
     * <pre>
     * fireworks:
     *   exposed-enum:
     *     enabled: true
     *     scan-packages:
     *       - com.yzm.fireworks.api.common.enums
     *       - com.yzm.other.enums
     * </pre>
     */
    private List<String> scanPackages = new ArrayList<>();

}
