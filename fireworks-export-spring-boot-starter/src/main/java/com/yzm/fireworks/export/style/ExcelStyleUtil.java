package com.yzm.fireworks.export.style;

import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import lombok.experimental.UtilityClass;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;

/**
 * 导出文件默认样式工具
 * <p>
 * 字体名称与字号由配置传入（{@code fireworks.export.font-name} / {@code fireworks.export.font-size}），
 * 避免在容器环境下依赖某个固定字体、也避免把排版参数写死在框架里。
 *
 * @author JYuan
 */
@UtilityClass
public class ExcelStyleUtil {

    public static WriteCellStyle getHeadStyle(String fontName, short fontSize) {
        WriteCellStyle writeCellStyle = new WriteCellStyle();

        // 不填充背景色(表头默认填充)
        writeCellStyle.setFillPatternType(FillPatternType.NO_FILL);

        // 设置字体
        WriteFont writeFont = new WriteFont();
        // 字体名称
        writeFont.setFontName(fontName);
        // 字体大小
        writeFont.setFontHeightInPoints(fontSize);
        // 是否加粗
        writeFont.setBold(false);
        writeCellStyle.setWriteFont(writeFont);

        // 边框
        writeCellStyle.setBorderTop(BorderStyle.NONE);
        writeCellStyle.setBorderBottom(BorderStyle.NONE);
        writeCellStyle.setBorderLeft(BorderStyle.NONE);
        writeCellStyle.setBorderRight(BorderStyle.NONE);

        // 自动换行
        writeCellStyle.setWrapped(false);
        // 水平左对齐
        writeCellStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        // 垂直居中
        writeCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return writeCellStyle;
    }

    public static WriteCellStyle getContentStyle(String fontName, short fontSize) {
        WriteCellStyle writeCellStyle = new WriteCellStyle();

        // 设置字体
        WriteFont writeFont = new WriteFont();
        // 字体名称
        writeFont.setFontName(fontName);
        // 字体大小
        writeFont.setFontHeightInPoints(fontSize);
        writeCellStyle.setWriteFont(writeFont);

        // 水平左对齐
        writeCellStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        // 垂直居中
        writeCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return writeCellStyle;
    }
}
