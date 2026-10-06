package com.yzm.fireworks.storage;

import lombok.experimental.UtilityClass;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 内容类型（ContentType / MIME）识别工具类。
 * <p>
 * 统一动词 {@code getContentType}，<b>由参数区分“手里有什么”</b>；返回类型统一为 {@code String}，
 * 识别不出时返回 {@link #DEFAULT_CONTENT_TYPE}（与 Tika、Servlet 的惯例一致）——
 * 调用方需要区分“未识别”时与之比较即可，不必再提供 {@code Optional} 版本和 {@code ...OrDefault} 版本。
 * <ul>
 *   <li>{@link #getContentType(String)}——只有文件名：查表，<b>不读内容</b>，成本最低</li>
 *   <li>{@link #getContentType(File)}——有 {@link File}：按内容嗅探（<b>推荐</b>，Tika 对 File 的效果最好）</li>
 *   <li>{@link #getContentType(String, InputStream)}——有流 + 文件名：按内容嗅探并用文件名细化</li>
 *   <li>{@link #getContentType(InputStream)}——只有流：仅凭字节签名嗅探</li>
 * </ul>
 * <b>分层策略，可信度从高到低</b>：① 生成方 / 客户端<b>显式声明</b>（最可靠，本类不介入）
 * → ② 按文件名查表 → ③ 按字节签名嗅探。
 * 入参容忍 URL 形态（自动剥离 {@code ?query} / {@code #fragment}）与后缀大小写差异，统一由 {@link FileNameSupport} 归一化。
 * <p>
 * 映射表取自 Tika 的 {@code tika-mimetypes.xml}（Freedesktop MIME-info）。与 Spring 内置表
 * （Spring Framework 6.2 的 {@code mime.types}，788 行）的实测差异：
 * <ul>
 *   <li>{@code .yaml} / {@code .yml} / {@code .md}：Spring 表<b>缺失</b>（返回 octet-stream），Tika 可识别</li>
 *   <li>{@code .toml}：两者都<b>未收录</b>，仍返回 octet-stream</li>
 * </ul>
 * 另外注意：Tika 返回的是<b>社区惯用名，不等于 RFC 注册名</b>——{@code .yaml} → {@code text/x-yaml}
 * （RFC 9512 注册名为 {@code application/yaml}）、{@code .md} → {@code text/x-web-markdown}
 * （注册名为 {@code text/markdown}）。若业务要求标准名，请在调用方显式声明该类型。
 * <p>
 * <b>能力边界（重要）</b>：内容嗅探只对<b>二进制格式</b>有效（图片 / PDF / 压缩包 / Office / 音视频等）。
 * YAML、JSON、CSV、Markdown 这类纯文本格式<b>没有魔数</b>，任何检测库都无法从字节判断其真实格式
 * ——Tika 对它们同样依赖文件名 glob 匹配。因此<b>后端自产文件必须显式声明类型</b>
 * （生成 YAML 就传 {@code application/yaml}），不要依赖检测。
 * <p>
 * <b>预签名直传的额外约束</b>：Content-Type 参与 SigV4 签名计算，签名时用什么类型、客户端就必须上传什么类型，
 * 否则 403 SignatureDoesNotMatch——这类场景必须使用签名时确定的类型，不要另行嗅探。
 */
@UtilityClass
public class ContentTypeUtil {

    /**
     * 兜底 MIME 类型：无法识别时的通用二进制类型（RFC 2046）。
     */
    public static final String DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM_VALUE;

    /**
     * 嗅探时允许的 mark 上限：Tika 的 magic 检测最多读取流头部 64KB。
     */
    private static final int SNIFF_MARK_LIMIT = 64 * 1024;

    private static final Tika TIKA = new Tika();

    // ─── 仅按文件名（不读取内容） ─────────────────────────────────────────────

    /**
     * 按文件名后缀查表推断 MIME 类型，<b>不读取内容</b>。
     * <p>
     * 例：{@code "config.yaml"} → {@code "text/x-yaml"}；{@code "cat.PNG?v=1"} → {@code "image/png"}
     *
     * @param filename 文件名或 URL，可为空白
     * @return 识别出的 MIME 类型；入参为空白或后缀不在表中时返回 {@link #DEFAULT_CONTENT_TYPE}
     */
    public static String getContentType(String filename) {
        String name = FileNameSupport.normalize(filename);
        if (name == null) {
            return DEFAULT_CONTENT_TYPE;
        }
        // Tika.detect(String) 按文件名 glob 匹配，无法识别时本身就返回 octet-stream
        return TIKA.detect(name);
    }

    // ─── 需要读取内容（magic number 嗅探） ────────────────────────────────────

    /**
     * 按文件内容嗅探 MIME 类型（<b>File 可用时优先用这个重载</b>：Tika 对文件的效果最好，
     * 且资源由 Tika 负责关闭）。
     * <p>
     * 检测时同时使用文件名与字节签名，优先级由 Tika 内部决定（magic → XML 根元素 → 文件名细化），
     * 因此<b>文件名不可靠也不影响结果</b>——例如内容是 PNG 而文件名是 {@code upload.bin}，仍会得到 {@code image/png}。
     *
     * @param file 待检测文件，不能为空
     * @return 识别出的 MIME 类型；检测失败时回退为按文件名推断
     */
    public static String getContentType(File file) {
        Assert.notNull(file, "file 不能为空");
        try {
            return TIKA.detect(file);
        } catch (IOException e) {
            return getContentType(file.getName());
        }
    }

    /**
     * 按内容嗅探 MIME 类型，并用文件名细化结果。
     * <p>
     * 实现说明：只读取流头部（Tika 上限 64KB），<b>不关闭</b>调用方的流；若流支持 mark/reset，
     * 检测后会尝试复位（超出 mark 上限时无法复位）。需要在检测后严格从头发读时，请调用方自行缓冲，
     * 或改用 {@link #getContentType(File)}。
     *
     * @param filename 文件名或 URL，可为空白（此时仅靠字节签名嗅探）
     * @param content  待检测的内容流；为 {@code null} 时退化为{@link #getContentType(String)}
     * @return 识别出的 MIME 类型；检测失败时回退为按文件名推断
     */
    public static String getContentType(String filename, InputStream content) {
        if (content == null) {
            return getContentType(filename);
        }
        Metadata metadata = new Metadata();
        String name = FileNameSupport.normalize(filename);
        if (name != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        }

        boolean resettable = content.markSupported();
        if (resettable) {
            content.mark(SNIFF_MARK_LIMIT);
        }
        try {
            return TIKA.detect(withoutClose(content), metadata);
        } catch (IOException e) {
            return getContentType(filename);
        } finally {
            if (resettable) {
                try {
                    content.reset();
                } catch (IOException ignored) {
                    // 复位失败仅意味着流位置已前移，不影响检测结果
                }
            }
        }
    }

    /**
     * 按内容嗅探 MIME 类型（无文件名可参考，仅凭字节签名）。
     * <p>
     * 注意：这是四种方式里信息最少的一种——能识别图片、PDF、压缩包等二进制格式，
     * 但对纯文本只能给出 {@code text/plain}。
     */
    public static String getContentType(InputStream content) {
        return getContentType(null, content);
    }

    /**
     * 包装为不可关闭流：检测只读取头部，不应影响调用方对流的所有权。
     */
    private static InputStream withoutClose(InputStream content) {
        return new FilterInputStream(content) {
            @Override
            public void close() {
                // 故意不关闭调用方的流
            }
        };
    }
}
