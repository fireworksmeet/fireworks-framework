package com.yzm.fireworks.storage.model.util;

import com.yzm.fireworks.common.constants.StringPool;
import lombok.experimental.UtilityClass;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ObjectKey 生成与规范化工具类：对外提供业务常用的对象键构造能力，以及路径与 Bucket 的规范化处理。
 * <p>
 * 业务层在直传 / 上传时通常需要按一定规则生成 objectKey，本工具类覆盖三种最常见模式，
 * 统一处理目录归一化、文件名后缀安全过滤与唯一标识生成，避免各业务自行拼接字符串出错。
 *
 * <pre>{@code
 * ObjectKeyUtil.buildDateKey("chat/files", "a.pdf");   // chat/files/2026/08/11/a1b2c3d4e5f6.pdf
 * ObjectKeyUtil.buildEntityKey("avatar/users", 10086L, "a.jpg"); // avatar/users/10086/c8f9d0a1b2c3.jpg
 * ObjectKeyUtil.buildHashKey("docs/receipts", "a.pdf"); // docs/receipts/a1/b2/a1b2c3d4e5f67890.pdf
 * ObjectKeyUtil.normalizeObjectKey("/avatar//2026/01.png"); // avatar/2026/01.png
 * }</pre>
 */
@UtilityClass
public class ObjectKeyUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 合法文件后缀：仅允许字母数字，防止畸形扩展名 / 路径穿越。 */
    private static final Pattern EXT_PATTERN = Pattern.compile("^[a-z0-9]+$");
    /** 去除路径首尾斜杠，防止拼出双斜杠 {@code //}。 */
    private static final Pattern LEADING_TRAILING_SLASH = Pattern.compile("^/+|/+$");
    /** 去除文件名前导斜杠，防止目录注入（如 {@code /etc/passwd}）。 */
    private static final Pattern LEADING_SLASH = Pattern.compile("^/+");
    /** 连续多个斜杠，用于归一化压缩为单个斜杠 {@code /}。 */
    private static final Pattern MULTIPLE_SLASHES = Pattern.compile("/{2,}");

    // ─── 1. 业务 ObjectKey 生成方法 ───────────────────────────────────────

    /**
     * 按业务模块 + 日期分区生成（最常用）。
     * 结构：{bizPrefix}/{yyyy/MM/dd}/{UUID}.{ext}
     * 示例：chat/files/2026/08/11/a1b2c3d4e5f6.pdf
     */
    public static String buildDateKey(String bizPrefix, String originalFilename) {
        String ext = getFileExtension(originalFilename);
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        String uuid = uuid();
        String subPath = datePath + StringPool.SLASH + uuid + suffix(ext);
        return joinPrefix(bizPrefix, subPath);
    }

    /**
     * 按用户/实体 ID 隔离生成（常用于头像、个人文档）。
     * 结构：{bizPrefix}/{entityId}/{UUID}.{ext}
     * 示例：avatar/users/10086/c8f9d0a1b2c3.jpg
     */
    public static String buildEntityKey(String bizPrefix, Object entityId, String originalFilename) {
        Assert.notNull(entityId, "entityId cannot be null");
        String ext = getFileExtension(originalFilename);
        String uuid = uuid();
        String subPath = entityId + StringPool.SLASH + uuid + suffix(ext);
        return joinPrefix(bizPrefix, subPath);
    }

    /**
     * 按 Hash 散列目录生成（适用于超大规模海量小文件，防止单个目录文件过多）。
     * 结构：{bizPrefix}/{UUID前2位}/{UUID第3-4位}/{UUID}.{ext}
     * 示例：docs/receipts/a1/b2/a1b2c3d4e5f67890.pdf
     */
    public static String buildHashKey(String bizPrefix, String originalFilename) {
        String ext = getFileExtension(originalFilename);
        String uuid = uuid();
        // 取 UUID 前 4 位做 2 级哈希目录
        String dir1 = uuid.substring(0, 2);
        String dir2 = uuid.substring(2, 4);
        String subPath = dir1 + StringPool.SLASH + dir2 + StringPool.SLASH + uuid + suffix(ext);
        return joinPrefix(bizPrefix, subPath);
    }

    /**
     * 按目录 + 文件名拼接 objectKey。
     * 示例：buildObjectKey("avatar/", "/a.jpg") = "avatar/a.jpg"
     */
    public static String buildObjectKey(String dir, String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return StringPool.EMPTY;
        }
        String cleanedDir = cleanPath(dir);
        String cleanedFileName = LEADING_SLASH.matcher(fileName.trim()).replaceAll(StringPool.EMPTY);
        String rawKey = cleanedDir.isEmpty() ? cleanedFileName : cleanedDir + StringPool.SLASH + cleanedFileName;
        return normalizeObjectKey(rawKey);
    }

    // ─── 2. 规范化与 Bucket 兜底方法 (本次新增/合并) ─────────────────────────

    /**
     * 规范化 ObjectKey：
     * 1. 移除前导斜杠（如 {@code "/avatar/a.jpg"} -> {@code "avatar/a.jpg"}）；
     * 2. 压缩连续多斜杠（如 {@code "avatar//2026///a.jpg"} -> {@code "avatar/2026/a.jpg"}）。
     *
     * @param objectKey 原始路径
     * @return 规范化后的合法 objectKey
     */
    public static String normalizeObjectKey(String objectKey) {
        Assert.hasText(objectKey, "objectKey 不能为空");
        String key = objectKey.trim();
        // 移除开头的斜杠
        key = LEADING_SLASH.matcher(key).replaceAll(StringPool.EMPTY);
        // 替换连续多个斜杠为单个斜杠
        key = MULTIPLE_SLASHES.matcher(key).replaceAll(StringPool.SLASH);
        Assert.hasText(key, "规范化后的 objectKey 不能为空");
        return key;
    }

    // ─── 3. 通用辅助工具方法 ─────────────────────────────────────────────

    /**
     * 拼接前缀与子路径，处理前缀为空的情况，避免生成前导斜杠。
     */
    private static String joinPrefix(String bizPrefix, String subPath) {
        String cleanedPrefix = cleanPath(bizPrefix);
        if (cleanedPrefix.isEmpty()) {
            return subPath;
        }
        return cleanedPrefix + StringPool.SLASH + subPath;
    }

    /**
     * 提取安全的文件后缀名（统一转小写，仅允许字母数字）。
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(StringPool.DOT) == -1) {
            return StringPool.EMPTY;
        }
        String ext = filename.substring(filename.lastIndexOf(StringPool.DOT) + 1).trim().toLowerCase();
        return EXT_PATTERN.matcher(ext).matches() ? ext : StringPool.EMPTY;
    }

    /**
     * 规范化目录路径：去除头尾斜杠，防止拼出双斜杠 {@code //}。
     */
    public static String cleanPath(String path) {
        if (!StringUtils.hasText(path)) {
            return StringPool.EMPTY;
        }
        return LEADING_TRAILING_SLASH.matcher(path.trim()).replaceAll(StringPool.EMPTY);
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace(StringPool.HYPHEN, StringPool.EMPTY);
    }

    private static String suffix(String ext) {
        return StringUtils.hasText(ext) ? StringPool.DOT + ext : StringPool.EMPTY;
    }
}