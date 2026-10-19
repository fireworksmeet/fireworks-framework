package com.yzm.fireworks.export;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 导出配置
 *
 * @author JYuan
 */
@ConfigurationProperties(prefix = "fireworks.export")
@Data
public class ExportProperties {

    /**
     * 导出临时文件目录；
     * 缺省为 {@code {java.io.tmpdir}/{spring.application.name}/export}
     */
    private String path;

    /**
     * 导出字体名称，默认 {@code 宋体}。
     * <p>
     * 注意：字体需要存在于运行环境（或由 Excel 客户端回退），Linux 容器通常没有宋体，
     * 云端预览可能显示为默认字体。可按部署环境改成 {@code 微软雅黑} / {@code Arial} 等。
     */
    private String fontName = "宋体";

    /**
     * 导出字体大小（磅），默认 {@code 11}。
     * <p>
     * 与 {@link #fontName} 一起构成导出文件的默认排版，可按业务视觉规范统一调整；
     * 单个导出需要更细的样式控制时，改用 {@code ExportContext.writeHandler} 注册自己的样式处理器。
     */
    private short fontSize = 11;
}
