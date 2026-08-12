package com.yzm.fireworks.export.core;

import com.yzm.fireworks.export.cursor.ExcelService;
import com.yzm.fireworks.export.model.ExportContext;
import com.yzm.fireworks.storage.model.dto.StorageFile;

import java.io.OutputStream;

/**
 * 核心导出服务接口（仅关注流与文件的生成，不感知 HTTP Web 概念）
 *
 * @author JYuan
 */
public interface ExcelExporter {

    /**
     * 将 Excel 数据流直接写入指定的 OutputSteam（支持 Web 响应流、文件输出流等）
     */
    <Q> void exportToStream(OutputStream outputStream, ExportContext<Q> context, ExcelService<Q> excelService);

    /**
     * 将大批量数据写入本地临时文件，并上传至存储系统 (OSS/S3)
     */
    <Q> StorageFile exportToStorage(ExportContext<Q> context, ExcelService<Q> excelService);
}