package com.yzm.fireworks.export;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.export")
@Data
public class ExportProperties {

    /**
     * 本地存放excel的路径(默认添加到/tmp/spring.application.name/excel)
     */
    private String path;
}
