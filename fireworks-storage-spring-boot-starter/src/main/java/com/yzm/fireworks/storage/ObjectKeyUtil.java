package com.yzm.fireworks.storage;

import com.yzm.fireworks.common.constants.StringPool;
import lombok.experimental.UtilityClass;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ObjectKey（对象键）生成与规范化工具类。
 * <p>
 * 设计原则：
 * <ul>
 *   <li><b>不使用原始文件名作为 key 主体</b>：key 由 UUID 构成，原始文件名只贡献后缀。
 *       从根上规避中文乱码、特殊字符、超长文件名、同名覆盖等问题</li>
 *   <li><b>失败即抛出</b>：参数非法时抛 {@link IllegalArgumentException}，不返回空串等哨兵值，
 *       避免非法 key 被静默写进存储</li>
 *   <li><b>统一产出规范化结果</b>：所有生成方法最终都会经过{@link #normalizeObjectKey(String)}</li>
 * </ul>
 * <p>
 * <b>安全边界</b>：S3 的 key 属于<b>扁平命名空间</b>，{@code "/"} 只是普通字符、{@code ".."} 没有目录语义，
 * 因此本类的规范化只为产出可预测的 key，<b>不构成路径穿越防护</b>。
 * 但反过来必须注意：<b>严禁把 objectKey 直接当作本地文件路径使用</b>，否则 {@code ".."} 会真的穿越目录。
 */
@UtilityClass
public class ObjectKeyUtil {

    /**
     * S3 协议对 objectKey 的长度上限（UTF-8 字节数）。
     */
    public static final int MAX_OBJECT_KEY_LENGTH = 1024;

    /**
     * 最长可识别后缀长度；超长视为非后缀，避免 {@code "a.somereallyreallylongextension"} 之类的畸形输入。
     */
    private static final int MAX_EXTENSION_LENGTH = 16;

    /**
     * 日期分区格式，如 {@code 2026/10/01}。
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 合法后缀：1~16 位小写字母或数字；含特殊字符、非 ASCII、超长的一律视为无后缀。 */
    private static final Pattern EXT_PATTERN = Pattern.compile("^[a-z0-9]{1," + MAX_EXTENSION_LENGTH + "}$");

    /** 剥离首尾斜杠，避免拼出 {@code //}。 */
    private static final Pattern LEADING_TRAILING_SLASH = Pattern.compile("^/+|/+$");

    /** 剥离前导斜杠（如 {@code "/etc/passwd"} → {@code "etc/passwd"}）。 */
    private static final Pattern LEADING_SLASH = Pattern.compile("^/+");

    /** 连续斜杠压缩为单个 {@code /}。 */
    private static final Pattern MULTIPLE_SLASHES = Pattern.compile("/{2,}");

    // ─── 1. 业务 ObjectKey 生成方法 ───────────────────────────────────────────

    /**
     * 按日期分区生成（最常用）。
     * <p>
     * 结果形如 {@code {bizPrefix}/{yyyy/MM/dd}/{uuid32}.{ext}}，
     * 例：{@code buildDateKey("avatar", "cat.png")} → {@code avatar/2026/10/01/3f2a...e91b.png}
     * <p>
     * 日期取服务器默认时区。
     *
     * @param bizPrefix        业务前缀（如 {@code "avatar"}、{@code "matchcard/photo"}），可为空
     * @param originalFilename 原始文件名，仅用于提取后缀，可为空（此时 key 无后缀）
     */
    public static String buildDateKey(String bizPrefix, String originalFilename) {
        return buildDateKey(bizPrefix, originalFilename, LocalDate.now());
    }

    /**
     * 按日期分区生成（显式指定分区日期）。
     * <p>
     * 把 {@code LocalDate.now()} 的隐式依赖显式化，供单元测试与历史数据补写使用
     * ——否则本方法在“跨日边界”上不可测。
     */
    public static String buildDateKey(String bizPrefix, String originalFilename, LocalDate partitionDate) {
        Assert.notNull(partitionDate, "partitionDate 不能为空");
        String subPath = partitionDate.format(DATE_FORMATTER)
                + StringPool.SLASH + uuid() + extensionSuffix(originalFilename);
        return joinPrefix(bizPrefix, subPath);
    }

    /**
     * 按实体 ID 隔离生成（头像、个人文档等“一个实体一个目录”的场景）。
     * <p>
     * 结果形如 {@code {bizPrefix}/{entityId}/{uuid32}.{ext}}，
     * 例：{@code buildEntityKey("avatar", "10086", "cat.png")} → {@code avatar/10086/3f2a...e91b.png}
     *
     * @param entityId 实体标识，不能为空白
     */
    public static String buildEntityKey(String bizPrefix, CharSequence entityId, String originalFilename) {
        Assert.notNull(entityId, "entityId 不能为空");
        Assert.hasText(entityId.toString(), "entityId 不能为空");
        String subPath = entityId + StringPool.SLASH + uuid() + extensionSuffix(originalFilename);
        return joinPrefix(bizPrefix, subPath);
    }

    /**
     * 按实体 ID（数值型主键）隔离生成，免去调用方到处写 {@code String.valueOf(id)}。
     * <p>
     * 例：{@code buildEntityKey("avatar", 10086L, "cat.png")} → {@code avatar/10086/3f2a...e91b.png}
     */
    public static String buildEntityKey(String bizPrefix, Long entityId, String originalFilename) {
        Assert.notNull(entityId, "entityId 不能为空");
        return buildEntityKey(bizPrefix, entityId.toString(), originalFilename);
    }

    /**
     * 按哈希散列目录生成（海量小文件场景，避免单目录下文件数过多）。
     * <p>
     * 结果形如 {@code {bizPrefix}/{xx}/{yy}/{uuid32}.{ext}}，
     * 例：{@code buildHashKey("upload", "a.jpg")} → {@code upload/3f/2a/3f2a...e91b.jpg}
     */
    public static String buildHashKey(String bizPrefix, String originalFilename) {
        String id = uuid();
        String subPath = id.substring(0, 2) + StringPool.SLASH
                + id.substring(2, 4) + StringPool.SLASH
                + id + extensionSuffix(originalFilename);
        return joinPrefix(bizPrefix, subPath);
    }

    /**
     * 按“目录 + 文件名”拼接 key，适用于调用方已自行保证文件名唯一的场景（如导出文件名自带时间戳/UUID）。
     * <p>
     * 例：{@code buildObjectKey("/export/", "2026-10-01_a1b2.xlsx")} → {@code export/2026-10-01_a1b2.xlsx}
     *
     * @param dir      目录前缀，可为空、可带首尾斜杠
     * @param fileName 文件名，不能为空
     * @throws IllegalArgumentException fileName 为空白，或拼接结果不是合法 key
     */
    public static String buildObjectKey(String dir, String fileName) {
        Assert.hasText(fileName, "fileName 不能为空");
        String cleanedDir = cleanPath(dir);
        String cleanedFileName = LEADING_SLASH.matcher(fileName.trim()).replaceAll(StringPool.EMPTY);
        String rawKey = cleanedDir.isEmpty()
                ? cleanedFileName
                : cleanedDir + StringPool.SLASH + cleanedFileName;
        return normalizeObjectKey(rawKey);
    }

    // ─── 2. 规范化方法 ────────────────────────────────────────────────────────

    /**
     * 规范化 objectKey：剥离前导斜杠、把连续斜杠压缩为单个，并校验非空与长度上限。
     * <p>
     * 例：{@code "/avatar//2026///a.jpg"} → {@code "avatar/2026/a.jpg"}
     *
     * @throws IllegalArgumentException key 为空白、规范化后为空、或超出 {@value #MAX_OBJECT_KEY_LENGTH} 字节
     */
    public static String normalizeObjectKey(String objectKey) {
        Assert.hasText(objectKey, "objectKey 不能为空");
        String key = MULTIPLE_SLASHES.matcher(
                LEADING_SLASH.matcher(objectKey.trim()).replaceAll(StringPool.EMPTY)
        ).replaceAll(StringPool.SLASH);
        Assert.hasText(key, "规范化后的 objectKey 不能为空");
        Assert.isTrue(key.getBytes(StandardCharsets.UTF_8).length <= MAX_OBJECT_KEY_LENGTH,
                () -> "objectKey 超出 S3 协议长度上限 " + MAX_OBJECT_KEY_LENGTH + " 字节");
        return key;
    }

    // ─── 3. 通用辅助工具方法 ──────────────────────────────────────────────────

    /**
     * 提取安全后缀（仅保留 1~16 位小写字母或数字）。
     * <ul>
     *   <li>入参先由 {@link FileNameSupport} 归一化：裁剪空白、剥离 {@code ?query} / {@code #fragment}、统一小写</li>
     *   <li>无后缀、隐藏文件（{@code ".env"}）、后缀含特殊字符或超长 → 返回空串</li>
     * </ul>
     * <p>
     * 例：{@code "cat.PNG"} → {@code "png"}；{@code "a.tar.gz"} → {@code "gz"}；{@code ".env"} → {@code ""}
     */
    public static String getFileExtension(String filename) {
        String name = FileNameSupport.normalize(filename);
        if (name == null) {
            return StringPool.EMPTY;
        }
        int dotIndex = name.lastIndexOf(StringPool.DOT);
        // dotIndex <= 0：既覆盖“无后缀”，也覆盖隐藏文件（.env 的点下标为 0）
        // dotIndex == length-1：形如 "a." 的尾点，同样视为无后缀
        if (dotIndex <= 0 || dotIndex == name.length() - 1) {
            return StringPool.EMPTY;
        }
        String ext = name.substring(dotIndex + 1);
        return EXT_PATTERN.matcher(ext).matches() ? ext : StringPool.EMPTY;
    }

    /**
     * 规范化目录前缀：裁剪空白并剥离首尾斜杠。
     * <p>
     * 例：{@code "/a/b/"} → {@code "a/b"}；{@code null} / {@code ""} / {@code "///"} → {@code ""}
     */
    public static String cleanPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return StringPool.EMPTY;
        }
        return LEADING_TRAILING_SLASH.matcher(path.trim()).replaceAll(StringPool.EMPTY);
    }

    /**
     * 拼接业务前缀与子路径；前缀为空时不产生前导斜杠，最终统一走规范化校验。
     */
    private static String joinPrefix(String bizPrefix, String subPath) {
        String cleanedPrefix = cleanPath(bizPrefix);
        return cleanedPrefix.isEmpty()
                ? normalizeObjectKey(subPath)
                : normalizeObjectKey(cleanedPrefix + StringPool.SLASH + subPath);
    }

    /**
     * 生成 32 位无连字符小写 UUID。
     */
    private static String uuid() {
        return UUID.randomUUID().toString().replace(StringPool.HYPHEN, StringPool.EMPTY);
    }

    /**
     * 由原始文件名得到带点的后缀（无法识别后缀时返回空串）。
     */
    private static String extensionSuffix(String originalFilename) {
        String ext = getFileExtension(originalFilename);
        return ext.isEmpty() ? StringPool.EMPTY : StringPool.DOT + ext;
    }
}
