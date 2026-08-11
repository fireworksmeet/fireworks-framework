package com.yzm.fireworks.storage.api;

import com.yzm.fireworks.common.constants.StringPool;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ObjectKey 生成工具类：对外提供业务常用的对象键（含路径的唯一标识）构造能力。
 * <p>
 * 业务层在直传 / 上传时通常需要按一定规则生成 objectKey，本工具类覆盖三种最常见模式，
 * 统一处理目录归一化、文件名后缀安全过滤与唯一标识生成，避免各业务自行拼接字符串出错。
 *
 * <pre>{@code
 * ObjectKeyUtil.buildDateKey("chat/files", "a.pdf");   // chat/files/2026/08/11/a1b2c3d4e5f6.pdf
 * ObjectKeyUtil.buildEntityKey("avatar/users", 10086L, "a.jpg"); // avatar/users/10086/c8f9d0a1b2c3.jpg
 * ObjectKeyUtil.buildHashKey("docs/receipts", "a.pdf"); // docs/receipts/a1/b2/a1b2c3d4e5f67890.pdf
 * }</pre>
 */
public final class ObjectKeyUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 合法文件后缀：仅允许字母数字，防止畸形扩展名 / 路径穿越。 */
    private static final Pattern EXT_PATTERN = Pattern.compile("^[a-z0-9]+$");
    /** 去除路径首尾斜杠，防止拼出双斜杠 {@code //}。 */
    private static final Pattern LEADING_TRAILING_SLASH = Pattern.compile("^/+|/+$");
    /** 去除文件名前导斜杠，防止目录注入（如 {@code /etc/passwd}）。 */
    private static final Pattern LEADING_SLASH = Pattern.compile("^/+" );

    private ObjectKeyUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 按业务模块 + 日期分区生成（最常用）。
     * 结构：{bizPrefix}/{yyyy/MM/dd}/{UUID}.{ext}
     * 示例：chat/files/2026/08/11/a1b2c3d4e5f6.pdf
     *
     * @param bizPrefix        业务前缀，如 {@code chat/files}
     * @param originalFilename 原始文件名（仅用于提取后缀，实际存储名由 UUID 决定）
     */
    public static String buildDateKey(String bizPrefix, String originalFilename) {
        String ext = getFileExtension(originalFilename);
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        String uuid = uuid();
        return cleanPath(bizPrefix) + StringPool.SLASH + datePath + StringPool.SLASH
                + uuid + suffix(ext);
    }

    /**
     * 按用户/实体 ID 隔离生成（常用于头像、个人文档）。
     * 结构：{bizPrefix}/{entityId}/{UUID}.{ext}
     * 示例：avatar/users/10086/c8f9d0a1b2c3.jpg
     *
     * @param bizPrefix        业务前缀，如 {@code avatar/users}
     * @param entityId         实体 ID（用户 ID、订单 ID 等），用于隔离目录
     * @param originalFilename 原始文件名（仅用于提取后缀）
     */
    public static String buildEntityKey(String bizPrefix, Object entityId, String originalFilename) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        String ext = getFileExtension(originalFilename);
        String uuid = uuid();
        return cleanPath(bizPrefix) + StringPool.SLASH + entityId + StringPool.SLASH
                + uuid + suffix(ext);
    }

    /**
     * 按 Hash 散列目录生成（适用于超大规模海量小文件，防止单个目录文件过多）。
     * 结构：{bizPrefix}/{UUID前2位}/{UUID第3-4位}/{UUID}.{ext}
     * 示例：docs/receipts/a1/b2/a1b2c3d4e5f67890.pdf
     *
     * @param bizPrefix        业务前缀，如 {@code docs/receipts}
     * @param originalFilename 原始文件名（仅用于提取后缀）
     */
    public static String buildHashKey(String bizPrefix, String originalFilename) {
        String ext = getFileExtension(originalFilename);
        String uuid = uuid();
        // 取 UUID 前 4 位做 2 级哈希目录
        String dir1 = uuid.substring(0, 2);
        String dir2 = uuid.substring(2, 4);
        return cleanPath(bizPrefix) + StringPool.SLASH + dir1 + StringPool.SLASH + dir2
                + StringPool.SLASH + uuid + suffix(ext);
    }

    /**
     * 按目录 + 文件名拼接 objectKey。
     * 结构：{dir}/{fileName}，dir 与 fileName 之间的斜杠由本方法统一处理（去除双斜杠）。
     * <p>
     * 对 fileName 仅剥离前导斜杠（防止目录注入），保留其中的 {@code /}（objectKey 本身即带斜杠路径）。
     * 示例：buildObjectKey("avatar/", "a.jpg") = "avatar/a.jpg"
     *
     * @param dir      目录（可为空，或带/不带末尾斜杠）
     * @param fileName 文件名
     */
    public static String buildObjectKey(String dir, String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return StringPool.EMPTY;
        }
        String cleaned = cleanPath(dir);
        return cleaned.isEmpty() ? fileName : cleaned + StringPool.SLASH + fileName;
    }

    /**
     * 提取安全的文件后缀名（统一转小写，仅允许字母数字）。
     *
     * @param filename 原始文件名
     * @return 后缀名（不含点），如 {@code jpg}；无后缀或含非法字符时返回空串
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(StringPool.DOT) == -1) {
            return StringPool.EMPTY;
        }
        String ext = filename.substring(filename.lastIndexOf(StringPool.DOT) + 1).trim().toLowerCase();
        // 简易安全过滤：只允许字母数字后缀，防止路径穿越或畸形扩展名
        return EXT_PATTERN.matcher(ext).matches() ? ext : StringPool.EMPTY;
    }

    /**
     * 规范化路径：去除头尾斜杠，防止拼出双斜杠 {@code //}。
     *
     * @param path 原始目录
     * @return 去除头尾斜杠后的目录；为 null 时返回空串
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
