package com.yzm.fireworks.security.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yzm.fireworks.common.util.JsonUtil;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 当前登录用户（认证上下文载荷）。
 *
 * <p><b>不可变 record</b>：由业务侧的 {@code SecurityUserResolver} 一次性构造（含角色与权限），
 * 框架只负责把它装配进 {@code SecurityContext}，不再回填任何字段。
 *
 * <p><b>角色与权限在构造时归一化</b>：去除首尾空白、丢弃 {@code null} / 空串 / 纯空白项、按归一化后去重。
 * 因此 {@link #roles()} / {@link #permissions()} 返回的就是可用于比较的干净值，
 * 不会出现"authority 转换后正确、直接读集合却匹配不上"的分裂。
 *
 * <p>{@code attributes} 建议只放简单类型（字符串 / 数字 / 布尔）：经 JSON 反序列化后
 * 类型可能被弱化，复杂对象请只放标识、用时再查。
 *
 * @author JYuan
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecurityUser(Long userId,
                           String username,
                           Set<String> roles,
                           Set<String> permissions,
                           Map<String, Object> attributes) {

    public SecurityUser {
        roles = normalizeCodes(roles);
        permissions = normalizeCodes(permissions);
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /**
     * 获取业务扩展字段（不做类型转换，由调用方保证类型正确）。
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 获取业务扩展字段并转换为指定类型。
     *
     * @param key  属性名
     * @param type 目标类型，不为 {@code null}
     * @return 转换后的值；属性不存在时返回 {@code null}
     */
    public <T> T getAttribute(String key, Class<T> type) {
        return JsonUtil.convertValue(attributes.get(key), type);
    }

    /**
     * 不输出 {@code attributes}：它来自外部输入，可能含 PII；而 Spring Security 的
     * {@code AbstractAuthenticationToken#toString()} 会打印 principal。
     */
    @Override
    public String toString() {
        return "SecurityUser[userId=" + userId + ", username=" + username
                + ", roles=" + roles.size() + ", permissions=" + permissions.size() + "]";
    }

    private static Set<String> normalizeCodes(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : values) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            // strip 与 hasText 使用同一套空白定义，避免 trim 留下全角空格
            normalized.add(raw.strip());
        }
        return Collections.unmodifiableSet(normalized);
    }
}
