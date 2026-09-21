package com.yzm.fireworks.security.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 当前登录用户信息（认证上下文载荷）
 * <p>
 * 由网关通过请求头透传，经 {@code HeaderTokenAuthenticationFilter} 解析后放入 SecurityContext。
 * <p>
 * 【约定】本对象只承载**身份与授权信息**，不放具体业务数据。
 * <p>
 * 【注意】实例统一由框架反序列化创建（{@code @NoArgsConstructor} + setter），
 * 业务侧只读取，**不应自行实例化**，因此不提供有参构造器。
 * <p>
 * {@code username / tenantId / attributes} 三者均来自网关透传的请求头原文，框架不做覆盖；
 * {@code roles / permissions} 则由 {@link SecurityAuthorityService} 在认证时装配（覆盖请求头中的同名字段）。
 * <p>
 * 【注意】{@code attributes} 建议只放**简单类型**（字符串 / 数字 / 布尔）：
 * 经 JSON 反序列化后类型可能被弱化（如整型变为 Long），复杂对象请只放标识、用时再查。
 * <p>
 * 【说明】用 {@code @Getter/@Setter} 而非 {@code @Data}：避免自动生成的 {@code toString()}
 * 把 {@link #attributes} 内容带入日志（Spring Security 的
 * {@code AbstractAuthenticationToken.toString()} 会打印 principal）。身份对象每请求新建，
 * {@code equals/hashCode} 亦无实际用途。
 *
 * @author JYuan
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 租户 ID（框架预留；当前项目为单租户，业务可不填）
     */
    private String tenantId;

    /**
     * 角色列表（如 ROLE_ADMIN、ROLE_FINANCE）
     * <p>认证时由 {@link SecurityAuthorityService#loadRoles(SecurityUser)} 装配
     */
    private Set<String> roles;

    /**
     * 权限 / 资源码列表（如 system:user:add），精确匹配，不支持通配符
     * <p>认证时由 {@link SecurityAuthorityService#loadPermissions(SecurityUser)} 装配
     */
    private Set<String> permissions;

    /**
     * 业务自定义扩展字段（来自网关透传的请求头，框架不覆盖）
     * <p>本框架为通用能力，不感知、也不应感知具体业务字段，故以无类型 Map 承载：
     * 业务想放什么就放什么，框架只负责透传与保存。
     * <p>业务侧若需要类型安全，可自行转换，无需框架提供额外机制：
     * {@code JsonUtil.convertValue(user.getAttributes(), BizAttrs.class)}
     * <p>恒不为 {@code null}：由字段初始化与 {@link #setAttributes} 共同保证，
     * 因此 {@link #setAttribute} 等读取方无需再判空
     */
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 设置业务扩展字段集合（标准 setter 语义：整体替换，不追加）
     * <p>入参为 {@code null} 时替换为空集合，而非"忽略"——setter 应始终完成赋值，
     * 否则反序列化等调用方会误以为已生效。字段非空不变式由本方法兜底。
     * <p>需要逐个追加请用 {@link #setAttribute}，或在业务侧
     * {@code getAttributes().putAll(...)}
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new HashMap<>() : attributes;
    }

    /**
     * 追加单个业务扩展字段（在现有集合上累加）
     */
    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * 获取业务扩展字段
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) this.attributes.get(key);
    }
}
