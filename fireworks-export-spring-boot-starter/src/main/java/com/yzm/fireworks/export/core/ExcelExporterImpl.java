package com.yzm.fireworks.export.core;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.yzm.fireworks.export.ExportProperties;
import com.yzm.fireworks.export.cursor.ExcelService;
import com.yzm.fireworks.export.model.ExportContext;
import com.yzm.fireworks.export.model.util.StyleUtil;
import com.yzm.fireworks.storage.model.dto.StorageFile;
import com.yzm.fireworks.storage.model.util.ObjectKeyUtil;
import com.yzm.fireworks.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.yzm.fireworks.common.constants.StringPool.UNDERSCORE;
import static com.yzm.fireworks.export.model.util.ExcelWebUtil.uuid;
import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;

/**
 * 导出服务核心实现
 *
 * @author JYuan
 */
@Slf4j
public class ExcelExporterImpl implements ExcelExporter {

    private final ExportProperties exportProperties;
    private final StorageService storageService;

    @Value("${spring.application.name:fireworks-export}")
    private String applicationName;

    private static final String EXPORT_DIR = "export";

    /**
     * 采用构造器注入，将 StorageService 声明为可选依赖
     */
    public ExcelExporterImpl(ExportProperties exportProperties, @Nullable StorageService storageService) {
        this.exportProperties = exportProperties;
        this.storageService = storageService;
    }

    private HorizontalCellStyleStrategy newStyleStrategy() {
        return new HorizontalCellStyleStrategy(StyleUtil.getHeadStyle(), StyleUtil.getContentStyle());
    }

    @Override
    public <Q> void exportToStream(OutputStream outputStream, ExportContext<Q> context, ExcelService<Q> excelService) {
        validateContext(context);

        ExcelWriterBuilder writerBuilder = EasyExcel.write(outputStream, context.getDataClass());
        writerBuilder.registerWriteHandler(newStyleStrategy());
        if (!ObjectUtils.isEmpty(context.getWriteHandler())) {
            writerBuilder.registerWriteHandler(context.getWriteHandler());
        }

        try (ExcelWriter excelWriter = writerBuilder.build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet(context.getSheetName()).build();
            excelService.writeData(excelWriter, writeSheet, context.getQueryParam());
            excelWriter.finish();
        }
    }

    @Override
    public <Q> StorageFile exportToStorage(ExportContext<Q> context, ExcelService<Q> excelService) {
        validateContext(context);
        Assert.notNull(storageService, "StorageService 未注入，无法使用上传存储服务");

        Path targetDir = getExportDirectory();
        String uniqueFileName = context.getFileName() + UNDERSCORE + uuid() + ".xlsx";
        File tempFile = targetDir.resolve(uniqueFileName).toFile();

        try {
            // 1. 写入本地临时文件
            ExcelWriterBuilder writerBuilder = EasyExcel.write(tempFile, context.getDataClass());
            writerBuilder.registerWriteHandler(newStyleStrategy());
            if (!ObjectUtils.isEmpty(context.getWriteHandler())) {
                writerBuilder.registerWriteHandler(context.getWriteHandler());
            }

            try (ExcelWriter excelWriter = writerBuilder.build()) {
                WriteSheet writeSheet = EasyExcel.writerSheet(context.getSheetName()).build();
                excelService.writeData(excelWriter, writeSheet, context.getQueryParam());
            }

            // 2. 上传至 OSS / 存储系统
            return storageService.upload(context.getBucket(), ObjectKeyUtil.buildObjectKey(EXPORT_DIR, uniqueFileName), tempFile, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        } catch (IOException e) {
            throw new IllegalStateException("Export and upload file failed", e);
        } finally {
            // 3. 严格清理本地临时文件
            if (tempFile.exists()) {
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (IOException e) {
                    log.warn("Failed to delete temporary export file: {}", tempFile.getAbsolutePath(), e);
                }
            }
        }
    }

    private void validateContext(ExportContext<?> context) {
        Assert.notNull(context, "ExportContext 不能为 null");
        Assert.hasText(context.getFileName(), "文件名不能为空");
        Assert.hasText(context.getSheetName(), "Sheet 名称不能为空");
        Assert.notNull(context.getDataClass(), "DataClass 不能为 null");
    }

    private Path getExportDirectory() {
        String path = exportProperties.getPath();
        Path excelPath = StringUtils.hasText(path)
                ? Paths.get(path)
                : Paths.get(JAVA_IO_TMPDIR, applicationName, EXPORT_DIR);

        if (!Files.exists(excelPath)) {
            try {
                Files.createDirectories(excelPath);
            } catch (FileAlreadyExistsException ignored) {
                // ignore
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create export directory: " + excelPath, e);
            }
        }
        return excelPath;
    }
}