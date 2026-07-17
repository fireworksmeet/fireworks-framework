package com.yzm.fireworks.export;

import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import org.apache.poi.ss.usermodel.*;

/**
 * @author JYuan
 */
public class StyleUtil {
    private static final String FONT_NAME = "宋体";
    private static final short FONT_SIZE = 11;

    public static WriteCellStyle getHeadStyle() {
        WriteCellStyle writeCellStyle = new WriteCellStyle();

        // 不填充背景色(表头默认填充)
        writeCellStyle.setFillPatternType(FillPatternType.NO_FILL);

        // 设置字体
        WriteFont writeFont = new WriteFont();
        // 字体名称
        writeFont.setFontName(FONT_NAME);
        // 字体大小
        writeFont.setFontHeightInPoints(FONT_SIZE);
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

    public static WriteCellStyle getContentStyle() {
        WriteCellStyle writeCellStyle = new WriteCellStyle();

        // 设置背景颜色
        // writeCellStyle.setFillBackgroundColor(IndexedColors.WHITE.getIndex());
        // 如果想背景色设置成功需要将设置下面的填充模式，默认是FillPatternType.NO_FILL
        // writeCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);

        // 设置字体
        WriteFont writeFont = new WriteFont();
        // 字体名称
        writeFont.setFontName(FONT_NAME);
        // 字体大小
        writeFont.setFontHeightInPoints(FONT_SIZE);
        writeCellStyle.setWriteFont(writeFont);

        // 水平左对齐
        writeCellStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);
        // 垂直居中
        writeCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return writeCellStyle;
    }
}
