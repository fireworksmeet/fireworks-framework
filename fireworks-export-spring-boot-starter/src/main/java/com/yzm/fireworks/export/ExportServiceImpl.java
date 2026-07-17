package com.yzm.fireworks.export;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.handler.WriteHandler;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.yzm.fireworks.common.util.ApplicationContextUtil;
import com.yzm.fireworks.id.IdType;
import com.yzm.fireworks.id.IdUtil;
import com.yzm.fireworks.storage.api.StorageFile;
import com.yzm.fireworks.storage.api.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.yzm.fireworks.common.constants.StringPool.UNDERSCORE;
import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;


/**
 * Excel 导出服务实现
 *
 * @author JYuan
 */
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final ExportProperties exportProperties;
    private final String sheetName = "Sheet";

    /**
     * 提供默认值 "fireworks-export"，避免宿主未配置 spring.application.name 时启动失败。
     */
    @Value("${spring.application.name:fireworks-export}")
    private String applicationName;

    public ExportServiceImpl(ExportProperties exportProperties) {
        this.exportProperties = exportProperties;
    }

    /**
     * 每次构建新的 HorizontalCellStyleStrategy 实例，避免多并发导出时共享有状态对象的线程安全问题。
     */
    private HorizontalCellStyleStrategy newStyleStrategy() {
        return new HorizontalCellStyleStrategy(StyleUtil.getHeadStyle(), StyleUtil.getContentStyle());
    }

    @Override
    public <D, Q> ResponseEntity<StreamingResponseBody> exportSmallData(String excelFileName,
                                                                        Class<D> dataClass,
                                                                        ExcelService<Q> excelService,
                                                                        Q queryParam) {
        return exportSmallData(excelFileName, sheetName, dataClass, excelService, queryParam);
    }

    @Override
    public <D, Q> ResponseEntity<StreamingResponseBody> exportSmallData(String excelFileName,
                                                                        String sheetName,
                                                                        Class<D> dataClass,
                                                                        ExcelService<Q> excelService,
                                                                        Q queryParam) {
        return exportSmallData(excelFileName, sheetName, dataClass, excelService, queryParam, null);
    }

    @Override
    public <D, Q> ResponseEntity<StreamingResponseBody> exportSmallData(String excelFileName,
                                                                        Class<D> dataClass,
                                                                        ExcelService<Q> excelService,
                                                                        Q queryParam,
                                                                        WriteHandler writeHandler) {
        return exportSmallData(excelFileName, sheetName, dataClass, excelService, queryParam, writeHandler);
    }

    @Override
    public <D, Q> ResponseEntity<StreamingResponseBody> exportSmallData(String excelFileName,
                                                                        String sheetName,
                                                                        Class<D> dataClass,
                                                                        ExcelService<Q> excelService,
                                                                        Q queryParam,
                                                                        WriteHandler writeHandler) {
        checkFileName(excelFileName);
        checkSheetName(sheetName);
        String encodedFileName = URLEncoder.encode(
                excelFileName + UNDERSCORE + System.currentTimeMillis() + ".xlsx", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        String.format("attachment;filename=%s;filename*=UTF-8''%s", encodedFileName, encodedFileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body((outputStream) -> {
                    ExcelWriterBuilder writerBuilder = EasyExcelFactory.write(outputStream, dataClass);
                    // 修复：每次新建 styleStrategy，避免并发共享
                    writerBuilder.registerWriteHandler(newStyleStrategy());
                    if (!ObjectUtils.isEmpty(writeHandler)) {
                        writerBuilder.registerWriteHandler(writeHandler);
                    }
                    try (ExcelWriter excelWriter = writerBuilder.build()) {
                        WriteSheet writeSheet = EasyExcel.writerSheet(sheetName).build();
                        excelService.writeData(excelWriter, writeSheet, queryParam);
                        excelWriter.finish();
                        outputStream.close();
                    }
                });
    }

    @Override
    public <D, Q> StorageFile exportLargeData(String bucketName, String dir, String excelFileName,
                                           Class<D> dataClass, ExcelService<Q> excelService, Q queryParam) {
        return exportLargeData(bucketName, dir, excelFileName, sheetName, dataClass, excelService, queryParam);
    }

    @Override
    public <D, Q> StorageFile exportLargeData(String bucketName, String dir, String excelFileName,
                                           String sheetName, Class<D> dataClass,
                                           ExcelService<Q> excelService, Q queryParam) {
        return exportLargeData(bucketName, dir, excelFileName, sheetName, dataClass, excelService, queryParam, null);
    }

    @Override
    public <D, Q> StorageFile exportLargeData(String bucketName, String dir, String excelFileName,
                                           Class<D> dataClass, ExcelService<Q> excelService,
                                           Q queryParam, WriteHandler writeHandler) {
        return exportLargeData(bucketName, dir, excelFileName, sheetName, dataClass, excelService, queryParam, writeHandler);
    }

    @Override
    public <D, Q> StorageFile exportLargeData(String bucketName, String dir, String excelFileName,
                                              String sheetName, Class<D> dataClass,
                                              ExcelService<Q> excelService, Q queryParam,
                                              WriteHandler writeHandler) {
        checkFileName(excelFileName);
        checkSheetName(sheetName);
        String path = exportProperties.getPath();
        Path excelPath;
        if (StringUtils.hasText(path)) {
            excelPath = Paths.get(path);
        } else {
            excelPath = Paths.get(JAVA_IO_TMPDIR, applicationName, "excel");
        }
        if (!Files.exists(excelPath)) {
            try {
                // easyexcel不会自动创建目录，只能写入已存在的目录
                Files.createDirectories(excelPath);
            } catch (FileAlreadyExistsException e) {
                log.debug("Directory already exists: {}", excelPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create export directory: " + excelPath, e);
            }
        }
        String fullFileName = excelPath + File.separator + excelFileName
                + UNDERSCORE + IdUtil.getId(IdType.FILE_SUFFIX) + ".xlsx";

        ExcelWriterBuilder writerBuilder = EasyExcel.write(fullFileName, dataClass);
        // 每次新建 styleStrategy，避免并发共享
        writerBuilder.registerWriteHandler(newStyleStrategy());
        if (!ObjectUtils.isEmpty(writeHandler)) {
            writerBuilder.registerWriteHandler(writeHandler);
        }
        try (ExcelWriter excelWriter = writerBuilder.build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet(sheetName).build();
            excelService.writeData(excelWriter, writeSheet, queryParam);
        }
        File file = new File(fullFileName);
        try {
            StorageService storageService = ApplicationContextUtil.getBean(StorageService.class);
            return storageService.uploadFile(bucketName, dir, file);
        } finally {
            // 清理临时文件
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", fullFileName, e);
            }
        }
    }

    private void checkFileName(String value) {
        Assert.isTrue(StringUtils.hasText(value), "文件名不能为空");
    }

    private void checkSheetName(String value) {
        Assert.isTrue(StringUtils.hasText(value), "Sheet 名称不能为空");
    }
}