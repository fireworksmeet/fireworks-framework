package com.yzm.fireworks.api.enums;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 选项枚举扫描配置属性
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.option-enum")
public class OptionEnumScanProperties {

    /**
     * 是否启用选项枚举自动扫描
     */
    private boolean enabled = false;

    /**
     * 扫描包路径列表
     * <p>
     * 支持配置多个包路径，例如：
     * <pre>
     * fireworks:
     *   option-enum:
     *     enabled: true
     *     scan-packages:
     *       - com.yzm.fireworks.api.common.enums
     *       - com.yzm.other.enums
     * </pre>
     */
    private List<String> scanPackages = new ArrayList<>();

}
