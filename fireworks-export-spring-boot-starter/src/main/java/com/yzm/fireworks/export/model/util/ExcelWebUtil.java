package com.yzm.fireworks.export.model.util;

import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.export.core.ExcelExporter;
import com.yzm.fireworks.export.cursor.ExcelService;
import com.yzm.fireworks.export.model.ExportContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.yzm.fireworks.common.constants.StringPool.UNDERSCORE;

/**
 * Web 端 HTTP 响应导出帮助类
 *
 * @author JYuan
 */
public class ExcelWebUtil {

    /**
     * 将导出数据直接封装为 Spring Web 的 ResponseEntity<StreamingResponseBody> 异步响应
     */
    public static <Q> ResponseEntity<StreamingResponseBody> writeToResponse(
            ExcelExporter exporter, 
            ExportContext<Q> context,
            ExcelService<Q> excelService) {

        String rawFileName = context.getFileName() + UNDERSCORE + uuid() + ".xlsx";
        String encodedFileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        String.format("attachment;filename=%s;filename*=UTF-8''%s", encodedFileName, encodedFileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(outputStream -> exporter.exportToStream(outputStream, context, excelService));
    }

    public static String uuid() {
        return UUID.randomUUID().toString().replace(StringPool.HYPHEN, StringPool.EMPTY);
    }
}