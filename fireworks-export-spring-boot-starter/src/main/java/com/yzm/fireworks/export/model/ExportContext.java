package com.yzm.fireworks.export.model;

import com.alibaba.excel.write.handler.WriteHandler;
import lombok.Builder;
import lombok.Getter;

/**
 * Excel 导出上下文对象
 *
 * @author JYuan
 */
@Getter
@Builder
public class ExportContext<Q> {

    /**
     * 基础文件名（如："用户导出列表"）
     * 1. 用于 Web 下载时的 Content-Disposition 请求头
     * 2. 作为本地临时文件名（实际文件名会追加随机后缀与 .xlsx）
     */
    private String fileName;

    /**
     * Sheet 名称，默认：Sheet1
     */
    @Builder.Default
    private String sheetName = "Sheet1";

    /**
     * 导出模型 Class
     */
    private Class<?> dataClass;

    /**
     * 查询参数
     */
    private Q queryParam;

    /**
     * 自定义写入策略/处理器 (可选)
     */
    private WriteHandler writeHandler;

}