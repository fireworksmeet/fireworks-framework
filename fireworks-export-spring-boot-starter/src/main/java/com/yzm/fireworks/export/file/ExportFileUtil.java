package com.yzm.fireworks.export.file;

import lombok.experimental.UtilityClass;

import java.util.UUID;
import java.util.regex.Pattern;

import static com.yzm.fireworks.common.constants.StringPool.EMPTY;
import static com.yzm.fireworks.common.constants.StringPool.HYPHEN;
import static com.yzm.fireworks.common.constants.StringPool.UNDERSCORE;

/**
 * 导出文件工具：文件名生成与内容类型常量。
 * <p>
 * 流式下载与落库两条路径共用同一套命名规则，避免「下载时叫 A、存储时叫 B」——
 * 文件名是业务名 + 随机后缀，既便于识别，也不会并发覆盖。
 *
 * @author JYuan
 */
@UtilityClass
public class ExportFileUtil {

    /**
     * xlsx 的标准 MIME 类型（Spring 的 {@code MediaType} 未提供常量）。
     * <p>
     * 由框架统一给出，避免各落库实现自己写死、写法不一致。
     */
    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String XLSX_SUFFIX = ".xlsx";

    private static final String DEFAULT_BASE_NAME = "export";

    /**
     * 文件名非法字符：Windows 保留字符 + 路径分隔符 + 控制字符
     */
    private static final Pattern ILLEGAL_NAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private static final int MAX_BASE_NAME_LENGTH = 80;

    /**
     * 生成唯一文件名：{@code 业务名_32位UUID.xlsx}。
     * <p>
     * 业务名中的路径分隔符与非法字符会被替换为 {@code _}（防止 {@code ../} 逃出临时目录），
     * 并截断到 80 字符；空名回落为 {@code export}。
     */
    public static String uniqueName(String baseName) {
        return sanitize(baseName) + UNDERSCORE + uuid() + XLSX_SUFFIX;
    }

    /**
     * 32 位紧凑格式 UUID（无连字符）。
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace(HYPHEN, EMPTY);
    }

    private static String sanitize(String baseName) {
        if (baseName == null) {
            return DEFAULT_BASE_NAME;
        }
        String safe = ILLEGAL_NAME_CHARS.matcher(baseName).replaceAll(UNDERSCORE).trim();
        if (safe.isEmpty()) {
            return DEFAULT_BASE_NAME;
        }
        return safe.length() > MAX_BASE_NAME_LENGTH ? safe.substring(0, MAX_BASE_NAME_LENGTH) : safe;
    }
}
