package com.yzm.fireworks.storage;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 存储模块<b>内部</b>共享的文件名归一化工具：包内可见，不属于对外 API。
 * <p>
 * 存在意义只有一个：{@link ContentTypeUtil} 与 {@link ObjectKeyUtil} 都需要把
 * “外部传入的文件名或 URL”先归一化成可直接取后缀的纯文件名。
 * 这套规则（裁剪空白、剥离查询串与锚点、统一小写）只应存在一份，否则两处会各自漂移。
 * <p>
 * 之所以在归一化阶段就统一小写：本类只服务后缀与 MIME 推断，而后缀本身不区分大小写；
 * 使用 {@link Locale#ROOT} 可避免土耳其语等 locale 下 {@code "I"} 的转换差异。
 */
@UtilityClass
class FileNameSupport {

    /**
     * 归一化文件名：裁剪首尾空白 → 剥离 URL 查询串（{@code ?}）与锚点（{@code #}）→ 统一小写。
     *
     * @param rawFileName 原始文件名或 URL，如 {@code " cat.PNG?v=1 "}、{@code "a/b/c.jpg"}
     * @return 归一化后的纯文件名（如 {@code "cat.png"}）；入参为空白时返回 {@code null}
     */
    static String normalize(String rawFileName) {
        if (!StringUtils.hasText(rawFileName)) {
            return null;
        }
        // 剥离后再 trim 一次：形如 "cat.png ?v=1" 的入参剥离后会留下尾部空白
        String name = stripQueryAndFragment(rawFileName.trim()).trim();
        return name.isEmpty() ? null : name.toLowerCase(Locale.ROOT);
    }

    /**
     * 剥离 URL 查询串（{@code ?}）与锚点（{@code #}）之后的内容；两者都不存在时原样返回。
     */
    private static String stripQueryAndFragment(String name) {
        int separatorIndex = firstSeparatorIndex(name);
        return separatorIndex < 0 ? name : name.substring(0, separatorIndex);
    }

    /**
     * 返回查询串与锚点中下标较小者的位置；两者都不存在时返回 {@code -1}。
     */
    private static int firstSeparatorIndex(String name) {
        int queryIndex = name.indexOf('?');
        int fragmentIndex = name.indexOf('#');
        if (queryIndex < 0) {
            return fragmentIndex;
        }
        if (fragmentIndex < 0) {
            return queryIndex;
        }
        return Math.min(queryIndex, fragmentIndex);
    }
}
