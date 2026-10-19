package com.yzm.fireworks.export.web;

import com.yzm.fireworks.export.core.ExcelExporter;
import com.yzm.fireworks.export.file.ExportFileUtil;
import com.yzm.fireworks.export.model.ExportContext;
import com.yzm.fireworks.export.provider.ExcelDataProvider;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Web 端 HTTP 响应导出工具类
 *
 * @author JYuan
 */
@UtilityClass
public class ExcelWebUtil {

    /**
     * 不支持 {@code filename*} 的老客户端使用的 ASCII 兜底文件名
     */
    private static final String ASCII_FALLBACK_NAME = "export.xlsx";

    /**
     * 将导出数据直接封装为 Spring Web 的 {@code ResponseEntity<StreamingResponseBody>} 异步响应。
     * <p>
     * 数据边生成边写出，不会在内存中攒出整个文件；但导出行数仍受响应超时与客户端等待时间约束。
     */
    public static <Q> ResponseEntity<StreamingResponseBody> writeToResponse(
            ExcelExporter exporter,
            ExportContext<Q> context,
            ExcelDataProvider<Q> dataProvider) {

        String fileName = ExportFileUtil.uniqueName(context.getFileName());
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        // RFC 6266：filename 给 ASCII 兜底，非 ASCII 名称只放在 filename* 里
        String contentDisposition = String.format("attachment;filename=\"%s\";filename*=UTF-8''%s",
                ASCII_FALLBACK_NAME, encodedFileName);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.parseMediaType(ExportFileUtil.XLSX_CONTENT_TYPE))
                .body(outputStream -> exporter.exportToStream(outputStream, context, dataProvider));
    }
}
