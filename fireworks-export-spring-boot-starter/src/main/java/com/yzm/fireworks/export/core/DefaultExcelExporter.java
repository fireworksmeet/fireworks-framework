package com.yzm.fireworks.export.core;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.yzm.fireworks.export.ExportProperties;
import com.yzm.fireworks.export.file.ExportFileStore;
import com.yzm.fireworks.export.file.ExportFileUtil;
import com.yzm.fireworks.export.model.ExportContext;
import com.yzm.fireworks.export.provider.ExcelDataProvider;
import com.yzm.fireworks.export.style.ExcelStyleUtil;
import lombok.extern.slf4j.Slf4j;
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

import static org.apache.commons.lang3.SystemUtils.JAVA_IO_TMPDIR;

/**
 * 默认导出执行器。
 * <p>
 * 本类只做两件事：把数据写成 Excel、保证临时文件被清理。「文件交给谁」通过
 * {@link ExportFileStore} 由调用方传入，因此不依赖任何存储 SDK。
 *
 * @author JYuan
 */
@Slf4j
public class DefaultExcelExporter implements ExcelExporter {

    private static final String EXPORT_DIR = "export";

    private final ExportProperties exportProperties;

    /**
     * 应用名，取值自 {@code spring.application.name}，用于默认临时目录与文件名定位。
     * <p>
     * 由 {@code ExportAutoConfiguration} 在创建本 Bean 时注入，而非使用字段上的
     * {@code @Value}：本实例由 {@code @Bean} 方法手动 {@code new} 构造，
     * 统一通过构造器传入依赖可保证「构造器即完整契约」、字段可声明为 {@code final}。
     */
    private final String applicationName;

    public DefaultExcelExporter(ExportProperties exportProperties, String applicationName) {
        this.exportProperties = exportProperties;
        this.applicationName = applicationName;
    }

    @Override
    public <Q> void exportToStream(OutputStream outputStream, ExportContext<Q> context, ExcelDataProvider<Q> dataProvider) {
        validateContext(context);
        Assert.notNull(outputStream, "输出流不能为 null");
        // autoCloseStream(false)：流由调用方管理。默认 true 会在导出结束时关闭调用方的流，
        // 导致「导出后继续写 ZipOutputStream / 继续追加内容」的用法直接报错
        write(EasyExcel.write(outputStream, context.getDataClass()).autoCloseStream(false),
                context, dataProvider);
    }

    @Override
    public <Q, R> R exportToFile(ExportContext<Q> context, ExcelDataProvider<Q> dataProvider,
                                 ExportFileStore<R> fileStore) {
        validateContext(context);
        Assert.notNull(fileStore, "ExportFileStore 不能为 null：文件落点由业务决定（对象存储 / 文件中台 / 本地）");

        String uniqueFileName = ExportFileUtil.uniqueName(context.getFileName());
        File tempFile = getExportDirectory().resolve(uniqueFileName).toFile();

        try {
            // 1. 写入本地临时文件（xlsx 是 zip 容器，无法像 CSV 一样纯流式追加）
            write(EasyExcel.write(tempFile, context.getDataClass()), context, dataProvider);

            // 2. 交给业务提供的落库实现。上传失败时异常原样向上抛：
            //    包装会丢掉更精确的上下文（bucket / key / cause），finally 仍保证临时文件被清理。
            return fileStore.save(tempFile, ExportFileUtil.XLSX_CONTENT_TYPE);
        } finally {
            // 3. 严格清理本地临时文件
            deleteQuietly(tempFile);
        }
    }

    /**
     * 统一的写入流程，供流式与落盘两种方式复用。
     */
    private <Q> void write(ExcelWriterBuilder writerBuilder, ExportContext<Q> context,
                           ExcelDataProvider<Q> dataProvider) {
        String fontName = exportProperties.getFontName();
        short fontSize = exportProperties.getFontSize();
        writerBuilder.registerWriteHandler(new HorizontalCellStyleStrategy(
                ExcelStyleUtil.getHeadStyle(fontName, fontSize), ExcelStyleUtil.getContentStyle(fontName, fontSize)));
        if (!ObjectUtils.isEmpty(context.getWriteHandler())) {
            writerBuilder.registerWriteHandler(context.getWriteHandler());
        }

        // 关闭时 ExcelWriter 自身会完成 flush + finish，无需再显式调用 finish()
        try (ExcelWriter excelWriter = writerBuilder.build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet(context.getSheetName()).build();
            dataProvider.writeData(excelWriter, writeSheet, context.getQueryParam());
        }
    }

    private void deleteQuietly(File tempFile) {
        if (!tempFile.exists()) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile.toPath());
        } catch (IOException e) {
            log.warn("Failed to delete temporary export file: {}", tempFile.getAbsolutePath(), e);
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
