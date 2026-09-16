package com.yzm.fireworks.oplog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 操作日志配置项
 * <p>
 * 前缀：{@code fireworks.oplog.record}
 * <p>
 * 说明：原版 mzt-biz-log 还包含一套基于 {@code java-object-diff} 的对象差异比对能力，
 * 由 addTemplate / updateTemplate / deleteTemplate 等十余个模板配置驱动。
 * 本项目移植时已移除该能力，故相应配置项一并删除，避免出现"有配置无实现"的误导；
 * 原先与之同名（diffLog）的配置项因语义上已与 diff 无关，已重命名为 skipUnrenderedLog。
 *
 * @author muzhantong
 */
@ConfigurationProperties(prefix = "fireworks.oplog.record")
@Data
public class LogRecordProperties {

    /**
     * 是否跳过"未真正渲染"的日志
     * <p>
     * 默认 false：当模板含 {@code #{}} 占位符、但渲染结果与模板原文完全相同时，
     * 判定为"占位符未生效"，跳过记录，避免产生无意义日志。
     * <p>
     * 设为 true 则不做此判断，一律记录。
     */
    private Boolean skipUnrenderedLog = false;

}
